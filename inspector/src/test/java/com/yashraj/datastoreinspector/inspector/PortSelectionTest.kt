package com.yashraj.datastoreinspector.inspector

import com.yashraj.datastoreinspector.inspector.server.SimpleHttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket

class PortSelectionTest {

    @Test
    fun usesFirstPortWhenFree() {
        assertEquals(5050 to "ok", startOnFirstFreePort(5050..5059) { "ok" })
    }

    @Test
    fun skipsPortsThatAreInUse() {
        val busy = setOf(5050, 5051)
        val result = startOnFirstFreePort(5050..5059) { port ->
            if (port in busy) throw BindException("in use") else "server@$port"
        }
        assertEquals(5052 to "server@5052", result)
    }

    @Test
    fun returnsNullWhenEveryPortIsInUse() {
        assertNull(startOnFirstFreePort(5050..5052) { throw BindException("in use") })
    }

    @Test
    fun propagatesFailuresOtherThanPortInUse() {
        assertThrows(IOException::class.java) {
            startOnFirstFreePort(5050..5059) { throw IOException("boom") }
        }
    }

    @Test
    fun fallsBackWhenARealSocketHoldsThePort() {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { taken ->
            val first = taken.localPort
            val (port, server) = startOnFirstFreePort(first..first + 9) { p ->
                object : SimpleHttpServer(p) {
                    override suspend fun serve(request: Request) =
                        respond(Status.OK, "text/plain", "")
                }.also { it.start() }
            } ?: error("no free port")
            try {
                assertEquals(true, port > first)
            } finally {
                server.stop()
            }
        }
    }
}
