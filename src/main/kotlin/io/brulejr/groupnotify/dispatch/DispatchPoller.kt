/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Calls [DispatchService.runDue] every poll interval, from once the application has
 * started until it stops. A poll that fails is logged and the next one tries again.
 */
class DispatchPoller(
    private val service: DispatchService,
    private val datafill: DispatchDatafill,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(DispatchPoller::class.java)

    @Volatile
    private var executor: ScheduledExecutorService? = null

    override fun start() {
        val scheduler = Executors.newSingleThreadScheduledExecutor { Thread(it, "dispatch-poller").apply { isDaemon = true } }
        val interval = datafill.pollInterval.toMillis()
        scheduler.scheduleWithFixedDelay(::poll, interval, interval, TimeUnit.MILLISECONDS)
        executor = scheduler
        log.info("dispatching due notifications every {}", datafill.pollInterval)
    }

    override fun stop() {
        executor?.shutdown()
        executor?.awaitTermination(30, TimeUnit.SECONDS)
        executor = null
    }

    override fun isRunning() = executor != null

    private fun poll() {
        try {
            val taken = service.runDue()
            if (taken > 0) log.info("dispatched {} due notification(s)", taken)
        } catch (e: Exception) {
            log.error("dispatch poll failed", e)
        }
    }
}
