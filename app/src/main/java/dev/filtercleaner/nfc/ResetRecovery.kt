// SPDX-License-Identifier: MIT
package dev.filtercleaner.nfc

/** Main-thread state: another tag cannot resolve an interrupted reset. */
internal class ResetRecovery(initialUid: String? = null) {
    var pendingUid: String? = initialUid
        private set

    fun begin(uid: String) {
        check(pendingUid == null) { "Check the pending tag before another reset." }
        pendingUid = uid
    }

    /** Call only after a successful read, a verified reset, or a known pre-write failure. */
    fun resolve(uid: String): Boolean {
        if (pendingUid != uid) return false
        pendingUid = null
        return true
    }
}
