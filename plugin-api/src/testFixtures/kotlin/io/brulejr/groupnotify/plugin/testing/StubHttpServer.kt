/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin.testing

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * An HTTP server on a free loopback port that records each request and answers with
 * [status] and [body]. Close it when done.
 */
class StubHttpServer(var status: Int = 200, var body: String = "{}") : AutoCloseable {

    data class Request(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    val requests = mutableListOf<Request>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val headers = exchange.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") }
            synchronized(requests) {
                requests += Request(
                    exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery, headers,
                    exchange.requestBody.readAllBytes().decodeToString(),
                )
            }
            val bytes = this@StubHttpServer.body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(this@StubHttpServer.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}
