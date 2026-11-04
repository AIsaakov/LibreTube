package com.github.libretube.player.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionRecoveryTest {
    private val recovery = SessionRecovery()
    private val newSessionReasons = mutableListOf<String>()

    /** Fetches once with [recovery], with the fetch attempts failing in the order of [failures]. */
    private fun fetch(vararg failures: Exception?) {
        val remaining = ArrayDeque(failures.toList())
        runBlocking {
            recovery.fetch(
                fetch = { remaining.removeFirstOrNull()?.let { throw it } },
                startNewSession = { newSessionReasons.add(it) }
            )
        }
    }

    @Test
    fun persistentReloadRequestStartsNewSession() {
        fetch(PlayerReloadRequestedException(), null)
        assertEquals(1, newSessionReasons.size)
    }

    @Test
    fun attestationRefusalStartsNewSession() {
        fetch(AttestationRequiredException(), null)
        assertEquals(1, newSessionReasons.size)
    }

    @Test
    fun refusalInNewSessionFails() {
        assertThrows(PlayerReloadRequestedException::class.java) {
            fetch(PlayerReloadRequestedException(), PlayerReloadRequestedException())
        }
        assertEquals(1, newSessionReasons.size)
    }

    @Test
    fun attestationRefusalAfterReloadRequestFails() {
        assertThrows(AttestationRequiredException::class.java) {
            fetch(PlayerReloadRequestedException(), AttestationRequiredException())
        }
        assertEquals(1, newSessionReasons.size)
    }

    @Test
    fun refusalAfterFailedRecoveryFailsWithoutNewSession() {
        assertThrows(PlayerReloadRequestedException::class.java) {
            fetch(PlayerReloadRequestedException(), PlayerReloadRequestedException())
        }
        assertThrows(PlayerReloadRequestedException::class.java) {
            fetch(PlayerReloadRequestedException())
        }
        assertEquals(1, newSessionReasons.size)
    }

    @Test
    fun successfulRequestAllowsAnotherRecovery() {
        fetch(PlayerReloadRequestedException(), null)
        fetch(AttestationRequiredException(), null)
        assertEquals(2, newSessionReasons.size)
    }

    @Test
    fun otherErrorFailsWithoutNewSession() {
        assertThrows(IllegalStateException::class.java) {
            fetch(IllegalStateException("HTTP request failed: 403"))
        }
        assertEquals(0, newSessionReasons.size)
    }
}
