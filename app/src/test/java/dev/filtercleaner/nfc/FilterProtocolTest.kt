// SPDX-License-Identifier: MIT
package dev.filtercleaner.nfc

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Original scripted tests of the requested raw command workflow. No NFC hardware is used. */
class FilterProtocolTest {
    private val uid = hex("5396F86401A700")

    @Test
    fun passwordGoldenVectors() {
        val vectors = listOf(
            "04A03CAA1E7080" to "CD91AFCC",
            "04112233445566" to "EC9805C8",
            "0404D972BA6C81" to "103AF4A2",
            "047FD6E2EA6C80" to "7D295EE7",
            "04FA3CAA1E7080" to "95BF2720",
            "5396F86401A700" to "67DCBB76",
        )
        for ((input, expected) in vectors) {
            assertArrayEquals(input, hex(expected), FilterProtocol.derivePassword(hex(input)))
        }
    }

    @Test
    fun passwordUsesUnsignedDigestIndexAndRawUidOrder() {
        // For this vector the unsigned base is BC, and the indices are 8, 13, 1, 5.
        val input = hex("04A03CAA1E7080")
        val original = input.copyOf()
        assertArrayEquals(hex("CD91AFCC"), FilterProtocol.derivePassword(input))
        assertArrayEquals(original, input)
    }

    @Test
    fun passwordReturnsAnIndependentArray() {
        val first = FilterProtocol.derivePassword(uid)
        first.fill(0)
        assertArrayEquals(hex("67DCBB76"), FilterProtocol.derivePassword(uid))
    }

    @Test
    fun passwordRejectsInvalidUidLengths() {
        for (length in listOf(0, 1, 4, 6, 8, 10)) {
            expectFailure<FilterException> { FilterProtocol.derivePassword(ByteArray(length)) }
        }
    }

    @Test
    fun passwordAcceptsEveryFirstUidByte() {
        for (firstByte in 0..255) {
            val input = uid.copyOf().also { it[0] = firstByte.toByte() }
            assertEquals(4, FilterProtocol.derivePassword(input).size)
        }
    }

    @Test
    fun inspectionStartsWithSampleAuthenticationThenReadsOnlyCounter() {
        val script = Script(*inspection(counter = 92122))
        assertEquals(FilterInfo("5396F86401A700", 92122), FilterProtocol(script, uid).inspect())
        script.assertDone()
    }

    @Test
    fun anotherGoldenUidUsesItsOwnPasswordAsFirstCommand() {
        val script = Script(
            reply("1B103AF4A2", hex("ABCD")),
            reply("3008", counterPages(1)),
        )
        val info = FilterProtocol(script, hex("0404D972BA6C81")).inspect()
        assertEquals(FilterInfo("0404D972BA6C81", 1), info)
        script.assertDone()
    }

    @Test
    fun anyTwoBytePackIsAccepted() {
        // Exercise every possible value of either PACK byte, including signed bytes.
        for (value in 0..255) {
            for (pack in listOf(byteArrayOf(value.toByte(), 0), byteArrayOf(0xFF.toByte(), value.toByte()))) {
                val script = Script(
                    reply("1B67DCBB76", pack),
                    reply("3008", counterPages(7)),
                )
                assertEquals(7L, FilterProtocol(script, uid).inspect().counter)
                script.assertDone()
            }
        }
    }

    @Test
    fun nonzeroPackAlsoAllowsReset() {
        val script = Script(
            reply("1B67DCBB76", hex("ABCD")),
            reply("3008", counterPages(7)),
            reply("A20800000000", hex("0A")),
            reply("3008", counterPages(0)),
        )
        assertTrue(FilterProtocol(script, uid).reset().wrote)
        script.assertDone()
    }

    @Test
    fun malformedPackStopsBeforeReadOrWrite() {
        for (length in listOf(0, 1, 3, 4, 16)) {
            val script = Script(reply("1B67DCBB76", ByteArray(length)))
            expectPreWriteFailure { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun authenticationNakStopsWithoutRetryOrWrite() {
        for (nak in listOf("00", "01", "04", "05", "0A", "FF")) {
            val script = Script(reply("1B67DCBB76", hex(nak)))
            expectPreWriteFailure { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun inspectionRejectsInvalidUidLengthsBeforeAnyCommand() {
        for (length in listOf(0, 1, 4, 6, 8, 10)) {
            val script = Script()
            expectPreWriteFailure { FilterProtocol(script, ByteArray(length)).inspect() }
            script.assertDone()
        }
    }

    @Test
    fun resetRejectsInvalidUidLengthsWithoutAnyWriteOrOtherCommand() {
        for (length in listOf(0, 1, 4, 6, 8, 10)) {
            val script = Script()
            expectPreWriteFailure { FilterProtocol(script, ByteArray(length)).reset() }
            script.assertDone()
        }
    }

    @Test
    fun engineCopiesUidAtConstruction() {
        val supplied = uid.copyOf()
        val script = Script(*inspection())
        val engine = FilterProtocol(script, supplied)
        supplied.fill(0)
        assertEquals("5396F86401A700", engine.inspect().uidHex)
        script.assertDone()
    }

    @Test
    fun counterIsUnsignedLittleEndian() {
        val values = listOf(0L, 1L, 128L, 255L, 256L, 92122L, 2147483648L, 4294967295L)
        for (value in values) {
            val script = Script(*inspection(counter = value))
            assertEquals(value, FilterProtocol(script, uid).inspect().counter)
            script.assertDone()
        }
    }

    @Test
    fun counterIgnoresTheOtherTwelveReadBytes() {
        val script = Script(
            reply("1B67DCBB76", hex("0000")),
            reply("3008", hex("DA670100FFFFFFFFFFFFFFFFFFFFFFFF")),
        )
        assertEquals(92122L, FilterProtocol(script, uid).inspect().counter)
        script.assertDone()
    }

    @Test
    fun initialReadMustHaveExactlySixteenBytesBeforeWriting() {
        for (length in listOf(0, 1, 4, 15, 17, 32)) {
            val script = Script(
                reply("1B67DCBB76", hex("0000")),
                reply("3008", ByteArray(length)),
            )
            expectPreWriteFailure { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun ioFailureAtEitherInspectionStepStopsWithoutRetry() {
        val commands = listOf("1B67DCBB76", "3008")
        for (index in commands.indices) {
            val cause = IOException("Contact lost")
            val steps = inspection().take(index) + failure(commands[index], cause)
            val script = Script(*steps.toTypedArray())
            val error = expectPreWriteFailure { FilterProtocol(script, uid).reset() }
            assertSame(cause, error.cause)
            script.assertDone()
        }
    }

    @Test
    fun resetHasExactAuthenticationReadWriteAndVerificationSequence() {
        val script = Script(
            *inspection(counter = 92122),
            reply("A20800000000", hex("0A")),
            reply("3008", counterPages(0)),
        )
        val result = FilterProtocol(script, uid).reset()
        assertEquals(FilterInfo("5396F86401A700", 92122), result.before)
        assertEquals(0L, result.afterCounter)
        assertTrue(result.wrote)
        script.assertDone()
    }

    @Test
    fun zeroCounterSkipsWriteAndUsesTheFreshRead() {
        val script = Script(*inspection(counter = 0))
        val result = FilterProtocol(script, uid).reset()
        assertEquals(0L, result.before.counter)
        assertEquals(0L, result.afterCounter)
        assertFalse(result.wrote)
        script.assertDone()
    }

    @Test
    fun eachInspectionAuthenticatesAgain() {
        val script = Script(*inspection(counter = 1), *inspection(counter = 2))
        val engine = FilterProtocol(script, uid)
        assertEquals(1L, engine.inspect().counter)
        assertEquals(2L, engine.inspect().counter)
        script.assertDone()
    }

    @Test
    fun resetDoesNotReuseEarlierInspectionOrCounter() {
        val script = Script(
            *inspection(counter = 0),
            *inspection(counter = 500),
            reply("A20800000000", hex("0A")),
            reply("3008", counterPages(0)),
        )
        val engine = FilterProtocol(script, uid)
        assertEquals(0L, engine.inspect().counter)
        assertEquals(500L, engine.reset().before.counter)
        script.assertDone()
    }

    @Test
    fun everyResetRepeatsAuthenticationAndCounterRead() {
        val script = Script(
            *inspection(),
            reply("A20800000000", hex("0A")),
            reply("3008", counterPages(0)),
            *inspection(counter = 0),
        )
        val engine = FilterProtocol(script, uid)
        assertTrue(engine.reset().wrote)
        assertFalse(engine.reset().wrote)
        script.assertDone()
    }

    @Test
    fun freshAuthenticationFailureBlocksResetAfterSuccessfulInspection() {
        val script = Script(*inspection(), reply("1B67DCBB76", hex("00")))
        val engine = FilterProtocol(script, uid)
        engine.inspect()
        expectPreWriteFailure { engine.reset() }
        script.assertDone()
    }

    @Test
    fun ackAcceptsAnyHighNibbleWithLowNibbleA() {
        for (high in 0..15) {
            val script = Script(
                *inspection(),
                reply("A20800000000", byteArrayOf(((high shl 4) or 0x0A).toByte())),
                reply("3008", counterPages(0)),
            )
            assertTrue(FilterProtocol(script, uid).reset().wrote)
            script.assertDone()
        }
    }

    @Test
    fun everyOtherAckLowNibbleIsUncertainAndStops() {
        for (low in 0..15) {
            if (low == 0x0A) continue
            val script = Script(*inspection(), reply("A20800000000", byteArrayOf(low.toByte())))
            expectUncertain { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun ackMustBeExactlyOneByte() {
        for (ack in listOf("", "0A00", "000A", "0A000000")) {
            val script = Script(*inspection(), reply("A20800000000", hex(ack)))
            expectUncertain { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun writeIoFailureIsUncertainWithoutRetryOrReadback() {
        val cause = IOException("Contact lost")
        val script = Script(*inspection(), failure("A20800000000", cause))
        val error = expectUncertain { FilterProtocol(script, uid).reset() }
        assertSame(cause, error.cause?.cause)
        script.assertDone()
    }

    @Test
    fun readbackIoFailureIsUncertainWithoutRetry() {
        val cause = IOException("Contact lost")
        val script = Script(
            *inspection(),
            reply("A20800000000", hex("0A")),
            failure("3008", cause),
        )
        val error = expectUncertain { FilterProtocol(script, uid).reset() }
        assertSame(cause, error.cause?.cause)
        script.assertDone()
    }

    @Test
    fun nonIoTransportFailureAfterWriteIsAlsoUncertain() {
        for (atWrite in listOf(true, false)) {
            val cause = IllegalStateException("Connection closed")
            val afterInspection = if (atWrite) {
                arrayOf(failure("A20800000000", cause))
            } else {
                arrayOf(reply("A20800000000", hex("0A")), failure("3008", cause))
            }
            val script = Script(*inspection(), *afterInspection)
            val error = expectUncertain { FilterProtocol(script, uid).reset() }
            assertSame(cause, error.cause)
            script.assertDone()
        }
    }

    @Test
    fun malformedReadbackIsUncertainEvenWithFourZeroBytes() {
        for (length in listOf(0, 1, 4, 15, 17, 32)) {
            val script = Script(
                *inspection(),
                reply("A20800000000", hex("0A")),
                reply("3008", ByteArray(length)),
            )
            expectUncertain { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun eachCounterByteMustBeZeroInReadback() {
        for (index in 0..3) {
            val readback = counterPages(0).also { it[index] = 0x80.toByte() }
            val script = Script(
                *inspection(),
                reply("A20800000000", hex("0A")),
                reply("3008", readback),
            )
            expectUncertain { FilterProtocol(script, uid).reset() }
            script.assertDone()
        }
    }

    @Test
    fun verificationDoesNotRequireOtherPagesToBeZero() {
        val readback = ByteArray(16) { 0xFF.toByte() }.also { it.fill(0, 0, 4) }
        val script = Script(
            *inspection(counter = 4294967295L),
            reply("A20800000000", hex("0A")),
            reply("3008", readback),
        )
        assertTrue(FilterProtocol(script, uid).reset().wrote)
        script.assertDone()
    }

    @Test
    fun uncertainWriteDoesNotStartRetriesAndAllowsExplicitInspection() {
        val script = Script(
            *inspection(),
            failure("A20800000000", IOException("Contact lost")),
            *inspection(counter = 0),
        )
        val engine = FilterProtocol(script, uid)
        expectUncertain { engine.reset() }
        assertEquals(0L, engine.inspect().counter)
        script.assertDone()
    }

    @Test
    fun authenticatedCounterReadSendsOnly3008() {
        val script = Script(reply("3008", hex("DA670100FFFFFFFFFFFFFFFFFFFFFFFF")))
        assertEquals(92122L, FilterProtocol(script, uid).readAuthenticatedCounter())
        script.assertDone()
    }

    @Test
    fun authenticatedPollingAfterInspectionDoesNotAuthenticateAgain() {
        val script = Script(
            *inspection(counter = 1),
            reply("3008", counterPages(2)),
            reply("3008", counterPages(3)),
        )
        val engine = FilterProtocol(script, uid)
        assertEquals(1L, engine.inspect().counter)
        assertEquals(2L, engine.readAuthenticatedCounter())
        assertEquals(3L, engine.readAuthenticatedCounter())
        script.assertDone()
    }

    @Test
    fun malformedAuthenticatedCounterReadIsNotWriteUncertain() {
        for (length in listOf(0, 1, 4, 15, 17, 32)) {
            val script = Script(reply("3008", ByteArray(length)))
            expectPreWriteFailure { FilterProtocol(script, uid).readAuthenticatedCounter() }
            script.assertDone()
        }
    }

    @Test
    fun authenticatedCounterReadDecodesPositiveUnsigned32BitValues() {
        for (value in listOf(0L, 128L, 256L, 2147483648L, 4294967295L)) {
            val script = Script(reply("3008", counterPages(value)))
            assertEquals(value, FilterProtocol(script, uid).readAuthenticatedCounter())
            script.assertDone()
        }
    }

    @Test
    fun authenticatedCounterIoFailureDoesNotRetryOrAuthenticate() {
        val cause = IOException("Contact lost")
        val script = Script(failure("3008", cause))
        val error = expectPreWriteFailure {
            FilterProtocol(script, uid).readAuthenticatedCounter()
        }
        assertSame(cause, error.cause)
        script.assertDone()
    }

    private fun inspection(counter: Long = 1L): Array<Step> = arrayOf(
        reply("1B67DCBB76", hex("0000")),
        reply("3008", counterPages(counter)),
    )

    private fun counterPages(value: Long): ByteArray = ByteArray(16).also {
        for (index in 0..3) it[index] = (value ushr (8 * index)).toByte()
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private class Step(val command: ByteArray, val respond: () -> ByteArray)

    private fun reply(command: String, response: ByteArray): Step =
        Step(hex(command)) { response.copyOf() }

    private fun failure(command: String, error: Exception): Step = Step(hex(command)) { throw error }

    private class Script(vararg steps: Step) : Transport {
        private val expected = steps.toList()
        private var position = 0

        override fun transceive(command: ByteArray): ByteArray {
            assertTrue("Unexpected command at step $position", position < expected.size)
            val step = expected[position++]
            assertArrayEquals("Command at step ${position - 1}", step.command, command)
            return step.respond()
        }

        fun assertDone() {
            assertEquals("Commands not sent", expected.size, position)
        }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        val caught = try {
            block()
            null
        } catch (failure: Throwable) {
            failure
        }
        assertNotNull("Expected ${T::class.java.simpleName}", caught)
        assertTrue("Unexpected exception: $caught", caught is T)
        return caught as T
    }

    private fun expectPreWriteFailure(block: () -> Unit): FilterException {
        val error = expectFailure<FilterException>(block)
        assertFalse(error is WriteUncertainException)
        return error
    }

    private fun expectUncertain(block: () -> Unit): WriteUncertainException {
        val error = expectFailure<WriteUncertainException>(block)
        assertTrue(error.message.orEmpty().contains("uncertain"))
        assertTrue(error.message.orEmpty().contains("Scan again"))
        assertNotNull(error.cause)
        return error
    }
}
