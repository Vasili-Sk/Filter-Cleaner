// SPDX-License-Identifier: MIT
package dev.filtercleaner.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetRecoveryTest {
    private val tagA = "5396F86401A700"
    private val tagB = "0404D972BA6C81"

    @Test
    fun newStateHasNoPendingTag() {
        assertNull(ResetRecovery().pendingUid)
    }

    @Test
    fun resetRecordsTheAffectedUid() {
        val state = ResetRecovery()
        state.begin(tagA)
        assertEquals(tagA, state.pendingUid)
    }

    @Test
    fun readingAnotherTagDoesNotResolveThePendingReset() {
        val state = ResetRecovery(tagA)
        assertFalse(state.resolve(tagB))
        assertEquals(tagA, state.pendingUid)
        assertTrue(state.resolve(tagA))
        assertNull(state.pendingUid)
    }

    @Test(expected = IllegalStateException::class)
    fun anotherResetCannotReplaceThePendingTag() {
        val state = ResetRecovery(tagA)
        state.begin(tagB)
    }

    @Test
    fun stateCanBeRestoredAndRequiresTheSameUid() {
        val before = ResetRecovery()
        before.begin(tagA)
        val restored = ResetRecovery(before.pendingUid)
        assertFalse(restored.resolve(tagB))
        assertTrue(restored.resolve(tagA))
    }

    @Test
    fun resolvingOneResetAllowsTheNext() {
        val state = ResetRecovery(tagA)
        state.resolve(tagA)
        state.begin(tagB)
        assertEquals(tagB, state.pendingUid)
        assertFalse(state.resolve(tagA))
    }

    @Test
    fun repeatedResolutionDoesNotChangeState() {
        val state = ResetRecovery(tagA)
        assertTrue(state.resolve(tagA))
        assertFalse(state.resolve(tagA))
        assertNull(state.pendingUid)
    }
}
