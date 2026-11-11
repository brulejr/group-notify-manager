/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.PluginContext
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

/**
 * Finds the delivery plugins and starts each.
 *
 * Plugins come from two places: jars on the class path, found by [ServiceLoader] (the
 * bundled ones, and any in the directory `loader.path` names), and plugins the
 * application declares as beans, such as the log plugin.
 */
class PluginLoader(
    private val datafill: DeliveryDatafill,
    private val contextFor: (DeliveryPlugin) -> PluginContext,
) {

    private val log = LoggerFactory.getLogger(PluginLoader::class.java)

    fun load(builtIn: List<DeliveryPlugin>, classLoader: ClassLoader): List<PluginHandle> {
        val discovered = ServiceLoader.load(DeliveryPlugin::class.java, classLoader).stream().map { provider ->
            runCatching { provider.get() to sourceOf(provider.type()) }
                .onFailure { log.error("could not create delivery plugin {}", provider.type().name, it) }
                .getOrNull()
        }.toList().filterNotNull()
        val all = builtIn.map { it to "built in" } + discovered
        all.groupBy { it.first.id }.filterValues { it.size > 1 }.forEach { (id, copies) ->
            error("delivery plugin id '$id' is provided more than once: ${copies.joinToString { "${it.first.javaClass.name} (${it.second})" }}")
        }
        return all.filter { (plugin, _) -> plugin.id !in datafill.disabled }.map { (plugin, source) ->
            require(ID.matches(plugin.id)) { "delivery plugin id '${plugin.id}' (${plugin.javaClass.name}) must be lower case letters, digits and dashes" }
            val error = runCatching { plugin.start(contextFor(plugin)) }
                .onFailure { log.error("delivery plugin {} failed to start", plugin.id, it) }
                .exceptionOrNull()
            PluginHandle(plugin, source, error?.let { it.message ?: it.javaClass.simpleName }).also {
                val status = it.status()
                log.info("delivery plugin {} ({}) from {}: {}", plugin.id, plugin.name, source, if (status.ready) "ready" else "not ready, ${status.reason}")
            }
        }
    }

    /**
     * The jar a plugin class came from, by file name. Inside the boot jar the location is
     * a nested URL, `jar:nested:/app/app.jar/!BOOT-INF/lib/notify-plugin-discord.jar!/`, so the
     * last `.jar` named is the plugin's own.
     */
    private fun sourceOf(type: Class<*>): String =
        runCatching { JAR.findAll(type.protectionDomain.codeSource.location.toString()).last().value }.getOrNull() ?: type.name

    private companion object {
        val ID = Regex("[a-z0-9][a-z0-9-]*")
        val JAR = Regex("[^/!]+\\.jar")
    }
}
