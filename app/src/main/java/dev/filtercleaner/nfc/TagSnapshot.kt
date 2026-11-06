// SPDX-License-Identifier: MIT
package dev.filtercleaner.nfc

import java.util.Locale

/** Informational user-memory snapshot. It does not determine reset compatibility. */
internal data class TagSnapshot(val productCode: String?, val hexPages: String) {
    companion object {
        fun parse(pages4: ByteArray, pages8: ByteArray): TagSnapshot {
            require(pages4.size == 16 && pages8.size == 16)
            val codeBytes = intArrayOf(2, 3, 6, 7).map { pages4[it].toInt() and 0xFF }
            val code = if (codeBytes.all { it in 32..126 }) {
                codeBytes.map { it.toChar() }.joinToString("")
            } else null
            val bytes = pages4 + pages8
            val hex = (4..11).joinToString("\n") { page ->
                val offset = (page - 4) * 4
                String.format(Locale.ROOT, "%02d: ", page) + bytes.copyOfRange(offset, offset + 4)
                    .joinToString(" ") { String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF) }
            }
            return TagSnapshot(code, hex)
        }
    }
}
