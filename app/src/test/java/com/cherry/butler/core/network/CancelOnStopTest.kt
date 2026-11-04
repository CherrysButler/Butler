package com.cherry.butler.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class CancelOnStopTest {

    /** A server that starts a stream, sends one line, then goes silent for [silentMs]. */
    private fun stallingServer(silentMs: Long): ServerSocket {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { s ->
                    s.getInputStream().read(ByteArray(4096))
                    val out = s.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
                    val line = "data: {\"x\":1}\n\n".toByteArray()
                    out.write("${Integer.toHexString(line.size)}\r\n".toByteArray()); out.write(line); out.write("\r\n".toByteArray())
                    out.flush()
                    Thread.sleep(silentMs)
                }
            }
        }
        return server
    }

    @Test
    fun `stop cuts a read waiting on a silent stream at once`() = runBlocking {
        val server = stallingServer(silentMs = 15_000)
        val client = OkHttpClient()
        var readFirst = false
        val reader = launch(Dispatchers.IO) {
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())
            val handle = currentCoroutineContext().job.cancelOnStop(call)
            try {
                call.execute().use { r ->
                    val source = r.body!!.source()
                    source.readUtf8Line()
                    readFirst = true
                    source.readUtf8Line() // blocks: the server has gone silent
                    source.readUtf8Line()
                }
            } catch (_: java.io.IOException) {
            } finally {
                handle.dispose()
            }
        }
        while (!readFirst) delay(10)
        delay(200)
        val stoppedAt = System.currentTimeMillis()
        reader.cancel()
        reader.join()
        val took = System.currentTimeMillis() - stoppedAt
        server.close()
        assertTrue("Stop took ${took} ms", took < 1_000)
    }
}
