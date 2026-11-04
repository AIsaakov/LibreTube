package com.github.libretube.player.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ServerUnreachableTest {
    @Test
    fun unresolvableHostIsUnreachable() {
        assertTrue(isServerUnreachable(UnknownHostException("Unable to resolve host")))
    }

    @Test
    fun missingRouteIsUnreachable() {
        assertTrue(isServerUnreachable(NoRouteToHostException("No route to host")))
    }

    @Test
    fun failedConnectionIsUnreachable() {
        assertTrue(isServerUnreachable(ConnectException("Network is unreachable")))
    }

    @Test
    fun wrappedUnresolvableHostIsUnreachable() {
        assertTrue(isServerUnreachable(IOException("request failed", UnknownHostException())))
    }

    @Test
    fun timeoutIsNotUnreachable() {
        assertFalse(isServerUnreachable(SocketTimeoutException("timeout")))
    }

    @Test
    fun httpErrorIsNotUnreachable() {
        assertFalse(isServerUnreachable(Exception("HTTP request failed: 403")))
    }
}
