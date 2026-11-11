/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.PluginLog
import org.slf4j.LoggerFactory

/** A plugin's log, under `notify.plugin.<id>`. */
class Slf4jPluginLog(pluginId: String) : PluginLog {

    private val log = LoggerFactory.getLogger("notify.plugin.$pluginId")

    override fun debug(message: String) = log.debug(message)

    override fun info(message: String) = log.info(message)

    override fun warn(message: String, error: Throwable?) = log.warn(message, error)

    override fun error(message: String, error: Throwable?) = log.error(message, error)
}
