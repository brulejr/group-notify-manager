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
 * [status] and [body], or with whatever [respond] returns for it when that is set, for
 * a plugin that makes several different calls. Close it when done.
 */
class StubHttpServer(var status: Int = 200, var body: String = "{}") : AutoCloseable {

    data class Request(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    /** An answer: the status and the body. */
    data class Response(val status: Int, val body: String)

    val requests = mutableListOf<Request>()

    /** Answers each request by itself, in place of [status] and [body]. */
    var respond: ((Request) -> Response)? = null

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val headers = exchange.requestHeaders.entries.associate { (k, v) -> k.lowercase() to v.joinToString(",") }
            val request = Request(
                exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery, headers,
                exchange.requestBody.readAllBytes().decodeToString(),
            )
            synchronized(requests) { requests += request }
            val answer = respond?.invoke(request) ?: Response(this@StubHttpServer.status, this@StubHttpServer.body)
            val bytes = answer.body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(answer.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl = "http://127.0.0.1:${server.address.port}"

    override fun close() = server.stop(0)
}
