package com.yashraj.datastoreinspector.inspector.server

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


internal abstract class SimpleHttpServer(private val port: Int) {

    enum class Status(val code: Int, val description: String) {
        OK(200, "OK"),
        BAD_REQUEST(400, "Bad Request"),
        NOT_FOUND(404, "Not Found")
    }

    // Parsed inbound request. headers keys are lower-cased. inputStream points at the body.
    class Request(
        val method: String,
        val uri: String,
        val headers: Map<String, String>,
        val inputStream: InputStream
    )

    // All responses are fixed-length — we always have the full body before writing.
    class Response(
        internal val status: Status,
        internal val mimeType: String,
        internal val body: ByteArray
    ) {
        constructor(status: Status, mimeType: String, body: String)
                : this(status, mimeType, body.toByteArray(Charsets.UTF_8))
    }

    // Override this to handle requests
    abstract fun serve(request: Request): Response

    protected fun respond(status: Status, mimeType: String, body: String): Response =
        Response(status, mimeType, body)

    @Volatile
    private var running = false  // @Volatile so accept-thread sees stop() immediately
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var workerPool: ExecutorService? = null  // reuses idle threads across requests

    fun start() {
        serverSocket = ServerSocket(port)
        running = true
        workerPool = Executors.newCachedThreadPool()
        acceptThread = Thread({
            while (running) {
                try {
                    val client = serverSocket?.accept() ?: break // blocks until browser connects
                    workerPool?.submit { handleClient(client) }  // hand off so we can accept next
                } catch (e: SocketException) {
                    if (running) Log.e(TAG, "Accept interrupted unexpectedly", e) // normal on stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Accept error", e)
                }
            }
        }, "SimpleHttpServer-accept").also { it.start() }
    }

    fun stop() {
        running = false
        serverSocket?.close()       // unblocks accept() via SocketException
        acceptThread?.join(2_000)   // wait for accept thread to exit
        workerPool?.shutdown()      // finish in-flight requests, accept no new ones
    }


    private fun handleClient(socket: Socket) {
        socket.soTimeout = 30_000
        socket.use {
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Parse request line: "PUT /api/sharedprefs/app_settings HTTP/1.1"
                val requestLine = readHttpLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val uri = parts[1].substringBefore('?') // drop query string if present

                // Parse headers until blank line, lower-case the keys
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break  // blank line = end of headers
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase()] =
                            line.substring(colon + 1).trim()
                    }
                }

                // input is now positioned at the first byte of the body
                val response = serve(Request(method, uri, headers, input))
                writeResponse(output, response)

            } catch (e: Exception) {
                Log.e(TAG, "Client error", e)
            }
        }
    }


    // Reads one line from input, stripping the trailing \r\n.
    private fun readHttpLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (baos.size() == 0) null else baos.toString(Charsets.UTF_8.name())
            if (b == '\n'.code && prev == '\r'.code) {
                val bytes = baos.toByteArray()
                return String(bytes, 0, maxOf(0, bytes.size - 1), Charsets.UTF_8) // drop the \r
            }
            baos.write(b)
            prev = b
        }
    }

    private fun writeResponse(output: OutputStream, response: Response) {
        try {
            val header = buildString {
                append("HTTP/1.1 ${response.status.code} ${response.status.description}\r\n")
                append("Content-Type: ${response.mimeType}\r\n")
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n")
                append("\r\n") // blank line separates headers from body
            }
            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(response.body)
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write error", e)
        }
    }

    companion object {
        private const val TAG = "SimpleHttpServer"
    }
}
