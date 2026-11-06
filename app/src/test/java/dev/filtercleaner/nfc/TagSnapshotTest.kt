// SPDX-License-Identifier: MIT
package dev.filtercleaner.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSnapshotTest {
    private val pages4 = hex("00004A44000041300019120500910070")
    private val pages8 = hex("59944D01000000000000000000000000")

    @Test
    fun extractsObservedProductCode() {
        assertEquals("JDA0", TagSnapshot.parse(pages4, pages8).productCode)
    }

    @Test
    fun doesNotRestrictOtherPrintableProductCodes() {
        val input = hex("00005859000039390000000000000000")
        assertEquals("XY99", TagSnapshot.parse(input, pages8).productCode)
    }

    @Test
    fun controlBytesDoNotBecomeProductText() {
        val input = pages4.copyOf().also { it[2] = 0 }
        assertNull(TagSnapshot.parse(input, pages8).productCode)
    }

    @Test
    fun formatsActualPagesWithoutTimeConversion() {
        val result = TagSnapshot.parse(pages4, pages8)
        assertEquals(8, result.hexPages.lines().size)
        assertEquals("04: 00 00 4A 44", result.hexPages.lines().first())
        assertEquals("08: 59 94 4D 01", result.hexPages.lines()[4])
        assertEquals("11: 00 00 00 00", result.hexPages.lines().last())
    }

    @Test
    fun inputChangesDoNotChangeTheCapturedSnapshot() {
        val first = pages4.copyOf()
        val second = pages8.copyOf()
        val result = TagSnapshot.parse(first, second)
        first.fill(0)
        second.fill(0)
        assertEquals("JDA0", result.productCode)
        assertTrue(result.hexPages.contains("08: 59 94 4D 01"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedFactoryPages() {
        TagSnapshot.parse(ByteArray(15), pages8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedCounterPages() {
        TagSnapshot.parse(pages4, ByteArray(15))
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) {
        value.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
