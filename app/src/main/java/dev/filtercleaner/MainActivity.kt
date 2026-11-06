// SPDX-License-Identifier: MIT
package dev.filtercleaner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import dev.filtercleaner.nfc.FilterException
import dev.filtercleaner.nfc.FilterInfo
import dev.filtercleaner.nfc.FilterProtocol
import dev.filtercleaner.nfc.ResetRecovery
import dev.filtercleaner.nfc.TagSnapshot
import dev.filtercleaner.nfc.Transport
import dev.filtercleaner.nfc.WriteUncertainException
import java.io.IOException
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Discovery and presence checks never write. Only the Cleanup filter button can reset. */
class MainActivity : Activity() {
    private lateinit var root: View
    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var uid: TextView
    private lateinit var productCode: TextView
    private lateinit var counter: TextView
    private lateinit var progress: ProgressBar
    private lateinit var cleanup: Button

    private var adapter: NfcAdapter? = null
    @Volatile private var foreground = false
    @Volatile private var channel: NfcA? = null
    private val generation = AtomicLong()
    private val worker = Executors.newSingleThreadScheduledExecutor()
    // All fields below, including recovery, belong to the main thread.
    private var readerEpoch = 0L
    private var readerEnabled = false
    private var receiverRegistered = false
    private var reading = false
    private var active: Session? = null
    private var selection: FilterInfo? = null
    private var poll: ScheduledFuture<*>? = null
    private var recovery = ResetRecovery()
    private var pendingDiscovery: Tag? = null
    private var failedTag: Tag? = null
    private val failedUids = mutableSetOf<String>()

    private class Session(
        val uidHex: String,
        val tag: Tag,
        val token: Long,
        val link: NfcA,
        val protocol: FilterProtocol,
        val snapshot: TagSnapshot?,
    ) {
        @Volatile var writing = false
        // A poll started before a button tap cannot overwrite the reset result.
        val revision = AtomicLong()
    }

    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED || !foreground) return
            if (!nfcEnabled()) {
                pendingDiscovery = null
                failedTag = null
                clearSession()
                stopReader()
                showWaitingState()
            } else if (!readerEnabled) {
                startReader()
            }
            updateControls()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // API 35 enforces edge-to-edge. Retain equivalent insets on API 26–34.
        if (Build.VERSION.SDK_INT in 30..34) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else if (Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        title = findViewById(R.id.statusTitle)
        message = findViewById(R.id.statusMessage)
        uid = findViewById(R.id.uidValue)
        productCode = findViewById(R.id.productValue)
        counter = findViewById(R.id.counterValue)
        progress = findViewById(R.id.scanProgress)
        cleanup = findViewById(R.id.cleanupButton)
        adapter = getSystemService(NfcManager::class.java)?.defaultAdapter
        recovery = ResetRecovery(savedInstanceState?.getString(RECOVERY_UID))
        // Never restore stale tag data or a stale enabled button from the view hierarchy.
        listOf(uid, productCode, counter, title, message, cleanup, progress).forEach {
            it.isSaveEnabled = false
        }
        applyInsets()
        title.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        cleanup.setText(R.string.flow_cleanup_button)
        cleanup.setOnClickListener {
            if (active == null && failedTag != null) retryRead() else cleanupHeldTag()
        }
        showDetails(null)
        showWaitingState()
        updateControls()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(nfcStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
            registerReceiver(nfcStateReceiver, filter)
        }
        receiverRegistered = true
        startReader()
    }

    override fun onPause() {
        foreground = false
        // Invalidate first, then abort blocking I/O. An interrupted reset stays pending.
        stopReader()
        pendingDiscovery = null
        failedTag = null
        clearSession()
        updateControls()
        if (receiverRegistered) {
            unregisterReceiver(nfcStateReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(RECOVERY_UID, recovery.pendingUid)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        generation.incrementAndGet()
        closeQuietly(channel)
        channel = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun applyInsets() {
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    left + insets.systemWindowInsetLeft, top + insets.systemWindowInsetTop,
                    right + insets.systemWindowInsetRight, bottom + insets.systemWindowInsetBottom,
                )
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun nfcEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    private fun startReader() {
        showWaitingState()
        if (!foreground || !nfcEnabled() || readerEnabled) {
            updateControls()
            return
        }
        val epoch = ++readerEpoch
        try {
            adapter?.enableReaderMode(
                this,
                { tag -> runOnUiThread { onTag(epoch, tag) } },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) },
            )
            readerEnabled = true
        } catch (_: Exception) {
            stopReader()
            status(R.string.flow_start_failed_title, R.string.flow_start_failed_message)
        }
        updateControls()
    }

    private fun stopReader() {
        ++readerEpoch
        readerEnabled = false
        try {
            adapter?.disableReaderMode(this)
        } catch (_: Exception) {
            // Includes stale-handle SecurityException and activity lifecycle errors.
        }
    }

    private fun onTag(epoch: Long, tag: Tag) {
        if (!foreground || !readerEnabled || epoch != readerEpoch) return
        if (reading || active?.writing == true) {
            pendingDiscovery = tag
            return
        }
        pendingDiscovery = null
        val rawUid = tag.id?.copyOf() ?: byteArrayOf()
        val uidHex = rawUid.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        if (active?.tag === tag) return
        clearSession()
        if (uidHex in failedUids) {
            failedTag = tag
            uid.text = uidHex
            showReadFailure()
            updateControls()
            return
        }
        failedTag = null
        val token = generation.get()
        reading = true
        uid.text = uidHex
        root.keepScreenOn = true
        status(R.string.flow_reading_title, R.string.flow_reading_message)
        updateControls()
        if (!submit {
                var link: NfcA? = null
                var held: Session? = null
                var info: FilterInfo? = null
                var failure: Exception? = null
                try {
                    ensureCurrent(token)
                    val connection = NfcA.get(tag) ?: throw FilterException("NfcA is not available.")
                    link = connection
                    channel = connection
                    ensureCurrent(token)
                    connection.connect()
                    ensureCurrent(token)
                    connection.timeout = 2500
                    val transport = Transport { command ->
                        ensureCurrent(token)
                        connection.transceive(command)
                    }
                    val protocol = FilterProtocol(transport, rawUid)
                    info = protocol.inspect()
                    // Optional user-data snapshot. Never read password/configuration pages.
                    val snapshot = try {
                        val pages4 = transport.transceive(byteArrayOf(0x30, 4))
                        val pages8 = transport.transceive(byteArrayOf(0x30, 8))
                        TagSnapshot.parse(pages4, pages8)
                    } catch (_: Exception) {
                        null
                    }
                    held = Session(uidHex, tag, token, connection, protocol, snapshot)
                } catch (error: Exception) {
                    failure = error
                } finally {
                    if (held == null || !isCurrent(token)) releaseLink(link)
                }
                val session = held
                val result = info
                val error = failure
                runOnUiThread {
                    if (isCurrent(token)) {
                        reading = false
                        root.keepScreenOn = false
                        if (error == null && session != null && result != null) {
                            active = session
                            selection = result
                            recovery.resolve(result.uidHex)
                            showDetails(result)
                            showDetected(result)
                            startPresencePoll(session)
                        } else {
                            clearSession()
                            uid.text = uidHex
                            failedUids.add(uidHex)
                            failedTag = tag
                            showReadFailure(error)
                        }
                        updateControls()
                        processPendingDiscovery()
                    }
                }
            }) {
            clearSession()
            showReadFailure()
            updateControls()
        }
    }

    private fun cleanupHeldTag() {
        val session = active ?: return
        val info = selection ?: return
        if (!foreground || !readerEnabled || !nfcEnabled() || reading || session.writing ||
            !isCurrent(session.token) || channel !== session.link || info.uidHex != session.uidHex ||
            info.counter == 0L || recovery.pendingUid != null
        ) return
        // The visible button and warning are the confirmation. No tag rescan is needed.
        recovery.begin(session.uidHex)
        session.writing = true
        session.revision.incrementAndGet()
        root.keepScreenOn = true
        status(R.string.flow_writing_title, R.string.flow_writing_message)
        updateControls()
        if (!submit {
                try {
                    ensureCurrent(session.token)
                    val result = session.protocol.reset()
                    runOnUiThread {
                        if (isActive(session)) {
                            session.writing = false
                            root.keepScreenOn = false
                            recovery.resolve(session.uidHex)
                            selection = result.before.copy(counter = result.afterCounter)
                            showDetails(selection)
                            status(
                                if (result.wrote) R.string.flow_reset_done_title else R.string.flow_zero_title,
                                if (result.wrote) R.string.flow_reset_done_message else R.string.flow_already_zero_message,
                            )
                            updateControls()
                            processPendingDiscovery()
                        }
                    }
                } catch (error: Exception) {
                    releaseLink(session.link)
                    runOnUiThread {
                        if (isActive(session)) {
                            if (error !is WriteUncertainException) recovery.resolve(session.uidHex)
                            clearSession()
                            if (recovery.pendingUid != null) showPendingReset()
                            else status(R.string.flow_reset_failed_title, R.string.flow_reset_failed_message)
                            updateControls()
                            processPendingDiscovery()
                        }
                    }
                }
            }) {
            // The executor rejected the task, so no reset command was sent.
            recovery.resolve(session.uidHex)
            clearSession()
            status(R.string.flow_reset_failed_title, R.string.flow_reset_failed_message)
            updateControls()
        }
    }

    private fun startPresencePoll(session: Session) {
        try {
            poll = worker.scheduleWithFixedDelay({
                if (isCurrent(session.token) && !session.writing) {
                    val revision = session.revision.get()
                    try {
                        ensureCurrent(session.token)
                        // Authentication is retained on this link. This helper sends only 30 08.
                        val currentCounter = session.protocol.readAuthenticatedCounter()
                        runOnUiThread {
                            if (isActive(session) && !session.writing && session.revision.get() == revision) {
                                if (selection?.counter != currentCounter) {
                                    val info = FilterInfo(session.uidHex, currentCounter)
                                    selection = info
                                    showDetails(info)
                                    showDetected(info)
                                    updateControls()
                                }
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread {
                            if (isActive(session) && !session.writing && session.revision.get() == revision) {
                                clearSession()
                                if (recovery.pendingUid != null) showPendingReset()
                                else status(R.string.flow_tag_lost_title, R.string.flow_tag_lost_message)
                                updateControls()
                            }
                        }
                    }
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            clearSession()
            showReadFailure()
        }
    }

    private fun processPendingDiscovery() {
        if (!foreground || !readerEnabled || reading || active?.writing == true) return
        val tag = pendingDiscovery ?: return
        pendingDiscovery = null
        onTag(readerEpoch, tag)
    }

    private fun retryRead() {
        val tag = failedTag ?: return
        val id = tag.id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        failedUids.remove(id)
        failedTag = null
        onTag(readerEpoch, tag)
    }

    private fun isCurrent(token: Long): Boolean = foreground && generation.get() == token

    private fun isActive(session: Session): Boolean = active === session && isCurrent(session.token)

    private fun ensureCurrent(token: Long) {
        if (!isCurrent(token)) throw IOException("NFC session ended.")
    }

    private fun submit(task: () -> Unit): Boolean = try {
        worker.execute { task() }
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    private fun clearSession() {
        generation.incrementAndGet()
        poll?.cancel(false)
        poll = null
        active = null
        selection = null
        reading = false
        root.keepScreenOn = false
        val connection = channel
        channel = null
        closeQuietly(connection)
        showDetails(null)
    }

    private fun releaseLink(link: NfcA?) {
        closeQuietly(link)
        if (channel === link) channel = null
    }

    private fun closeQuietly(link: NfcA?) {
        try {
            link?.close()
        } catch (_: Exception) {
            // Includes IOException and SecurityException. Never log tag secrets.
        }
    }

    private fun showWaitingState() {
        when {
            adapter == null -> status(R.string.flow_no_nfc_title, R.string.flow_no_nfc_message)
            !nfcEnabled() -> status(R.string.flow_nfc_off_title, R.string.flow_nfc_off_message)
            recovery.pendingUid != null -> showPendingReset()
            else -> status(R.string.flow_wait_title, R.string.flow_wait_message)
        }
    }

    private fun showDetected(info: FilterInfo) {
        when {
            recovery.pendingUid != null -> showPendingReset()
            info.counter == 0L -> status(R.string.flow_zero_title, R.string.flow_zero_message)
            else -> status(R.string.flow_detected_title, R.string.flow_detected_message)
        }
    }

    private fun showReadFailure(error: Exception? = null) {
        if (recovery.pendingUid != null) showPendingReset()
        else {
            status(R.string.flow_read_failed_title, R.string.flow_read_failed_message)
            if (error is FilterException) {
                message.text = getString(
                    R.string.flow_read_failure_detail, error.message, getString(R.string.flow_read_failed_message),
                )
            }
        }
    }

    private fun showPendingReset() {
        title.setText(R.string.flow_uncertain_title)
        message.text = getString(R.string.flow_uncertain_message, recovery.pendingUid.orEmpty())
    }

    private fun showDetails(info: FilterInfo?) {
        val empty = getString(R.string.flow_unknown)
        uid.text = info?.uidHex ?: empty
        val snapshot = if (info == null) null else active?.snapshot
        productCode.text = snapshot?.productCode ?: empty
        counter.text = info?.counter?.let { NumberFormat.getIntegerInstance().format(it) } ?: empty
    }

    private fun status(titleId: Int, messageId: Int) {
        title.setText(titleId)
        message.setText(messageId)
    }

    private fun updateControls() {
        val session = active
        val info = selection
        val canRetry = session == null && failedTag != null && !reading
        cleanup.setText(if (canRetry) R.string.flow_retry_button else R.string.flow_cleanup_button)
        val canReset = session != null && isCurrent(session.token) && !session.writing &&
            channel === session.link && info != null && info.uidHex == session.uidHex &&
            info.counter != 0L && recovery.pendingUid == null
        cleanup.isEnabled = foreground && readerEnabled && nfcEnabled() && !reading && (canRetry || canReset)
        progress.visibility = if (reading || session?.writing == true) View.VISIBLE else View.GONE
    }

    companion object {
        private const val RECOVERY_UID = "unresolvedResetUid"
    }
}
