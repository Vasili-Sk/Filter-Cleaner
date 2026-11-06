// SPDX-License-Identifier: MIT
package dev.filtercleaner.nfc

import java.io.IOException
import java.security.MessageDigest

fun interface Transport {
    fun transceive(command: ByteArray): ByteArray
}

open class FilterException(message: String, cause: Throwable? = null) : IOException(message, cause)

class WriteUncertainException(
    message: String = "Write result is uncertain. Scan again in read-only mode.",
    cause: Throwable? = null,
) : FilterException(message, cause)

data class FilterInfo(val uidHex: String, val counter: Long)

data class ResetResult(val before: FilterInfo, val afterCounter: Long, val wrote: Boolean)

/** The caller owns NfcA selection, the connection, user confirmation, and thread selection. */
class FilterProtocol(private val transport: Transport, uid: ByteArray) {
    private val uid = uid.copyOf()

    fun inspect(): FilterInfo {
        val password = derivePassword(uid)
        val pack = exchange(byteArrayOf(0x1B) + password)
        if (pack.size != 2) {
            throw FilterException("Authentication failed.")
        }
        val counter = readAuthenticatedCounter()
        val hex = "0123456789ABCDEF"
        val uidHex = buildString {
            for (byte in uid) {
                val value = byte.toInt() and 0xFF
                append(hex[value ushr 4])
                append(hex[value and 0x0F])
            }
        }
        return FilterInfo(uidHex, counter)
    }

    /** Each call authenticates again. A write is never retried. */
    fun reset(): ResetResult {
        val before = inspect()
        if (before.counter == 0L) {
            return ResetResult(before, afterCounter = 0L, wrote = false)
        }

        // Enter the uncertain-outcome boundary before sending the write command.
        try {
            val ack = exchange(byteArrayOf(0xA2.toByte(), 0x08, 0, 0, 0, 0))
            if (ack.size != 1 || (ack[0].toInt() and 0x0F) != 0x0A) {
                throw FilterException("Invalid write response.")
            }
            val after = readAuthenticatedCounter()
            if (after != 0L) {
                throw FilterException("Counter reset was not verified.")
            }
            return ResetResult(before, afterCounter = after, wrote = true)
        } catch (failure: Exception) {
            throw WriteUncertainException(cause = failure)
        }
    }

    private fun exchange(command: ByteArray): ByteArray = try {
        transport.transceive(command)
    } catch (failure: IOException) {
        throw FilterException("Tag communication failed.", failure)
    }

    /**
     * The caller must keep the same previously authenticated connection open.
     * Sends only 30 08. Does not send PWD_AUTH, retry, or write.
     */
    fun readAuthenticatedCounter(): Long {
        val pages = exchange(byteArrayOf(0x30, 0x08))
        if (pages.size != 16) {
            throw FilterException("Invalid read response length.")
        }
        var value = 0L
        for (index in 0..3) {
            value = value or ((pages[index].toLong() and 0xFFL) shl (8 * index))
        }
        return value
    }

    companion object {
        /** Hash the seven raw UID bytes; use an unsigned first digest byte as the base. */
        fun derivePassword(uid: ByteArray): ByteArray {
            if (uid.size != 7) {
                throw FilterException("Unsupported tag UID length.")
            }
            val digest = MessageDigest.getInstance("SHA-1").digest(uid)
            val base = digest[0].toInt() and 0xFF
            return intArrayOf(0, 5, 13, 17)
                .map { digest[(base + it) % digest.size] }
                .toByteArray()
        }
    }
}
