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
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginField
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * The delivery plugins, and every call the rest of the application makes to them: what
 * a target must hold, how it is summarised without its secrets, and the delivery itself
 * with its retries.
 *
 * Nothing outside this class calls a plugin, so a misbehaving one, throwing where it
 * should return a failure, is contained here.
 */
class DeliveryService(
    handles: List<PluginHandle>,
    private val datafill: DeliveryDatafill,
    private val sleep: (Duration) -> Unit = { Thread.sleep(it) },
) {

    private val log = LoggerFactory.getLogger(DeliveryService::class.java)
    private val byId = handles.associateBy { it.id }
    private val delivered = AtomicLong()
    private val failed = AtomicLong()
    private val retries = AtomicLong()

    /** Every loaded plugin, by name. */
    fun plugins(): List<PluginDescriptor> = byId.values.sortedBy { it.plugin.name.lowercase() }.map(::describe)

    fun plugin(id: String): PluginDescriptor? = byId[id]?.let(::describe)

    /** A target's values limited to the plugin's fields, trimmed, with checkboxes as `true` or `false`. */
    fun normalize(pluginId: String, values: Map<String, String>): Map<String, String> {
        val plugin = byId[pluginId]?.plugin ?: return values
        return plugin.fields.mapNotNull { field ->
            val value = values[field.key]?.trim().orEmpty()
            when {
                field.type == FieldType.CHECKBOX -> field.key to (value == "true" || value == "on").toString()
                value.isEmpty() -> null
                else -> field.key to value
            }
        }.toMap()
    }

    /**
     * Fills secret fields left blank from [previous], the stored target of the same
     * delivery: the console never shows a secret again, so blank means unchanged.
     */
    fun keepSecrets(pluginId: String, values: Map<String, String>, previous: Map<String, String>?): Map<String, String> {
        if (previous == null) return values
        val secrets = secretKeys(pluginId)
        return values + previous.filterKeys { it in secrets && (values[it].isNullOrBlank() || values[it] == MASK) }
    }

    /** [values] with every secret replaced by [MASK], for showing. */
    fun mask(pluginId: String, values: Map<String, String>): Map<String, String> {
        val secrets = secretKeys(pluginId)
        return values.mapValues { (k, v) -> if (k in secrets && v.isNotEmpty()) MASK else v }
    }

    /** Every problem with a target, keyed by field key; an unknown plugin under `plugin`. */
    fun validate(pluginId: String, values: Map<String, String>): List<FieldProblem> {
        val plugin = byId[pluginId]?.plugin ?: return listOf(FieldProblem("plugin", "is not an installed plugin"))
        val own = plugin.fields.flatMap { field -> problems(field, values[field.key]) }
        if (own.isNotEmpty()) return own
        return runCatching { plugin.validate(DeliveryTarget(values)) }
            .getOrElse { listOf(FieldProblem("plugin", "could not check this target: ${it.message}")) }
    }

    /** Where a target goes, without its secrets. */
    fun summarize(pluginId: String, values: Map<String, String>): String {
        val plugin = byId[pluginId]?.plugin ?: return "$pluginId (not installed)"
        return runCatching { plugin.summarize(DeliveryTarget(values)) }.getOrElse { plugin.name }
    }

    /**
     * Delivers [message] to one target, trying again after a retryable failure up to
     * `notify.delivery.max-attempts` times. Blocks for the backoff between tries.
     */
    fun deliver(pluginId: String, message: NotificationMessage, values: Map<String, String>): DeliveryOutcome {
        val handle = byId[pluginId]
            ?: return DeliveryOutcome(DeliveryResult.Failed("plugin '$pluginId' is not installed"), 0).also { failed.incrementAndGet() }
        val status = handle.status()
        if (!status.ready) {
            failed.incrementAndGet()
            return DeliveryOutcome(DeliveryResult.Failed("${handle.plugin.name} is not ready: ${status.reason}"), 0)
        }
        var attempt = 0
        var backoff = datafill.retryBackoff
        while (true) {
            attempt++
            val result = attemptOnce(handle.plugin, message, DeliveryTarget(values))
            val retry = result is DeliveryResult.Failed && result.retryable && attempt < datafill.maxAttempts
            if (!retry) {
                (if (result is DeliveryResult.Delivered) delivered else failed).incrementAndGet()
                return DeliveryOutcome(result, attempt)
            }
            retries.incrementAndGet()
            log.info("{} delivery of '{}' failed ({}); retrying in {}", pluginId, message.title, (result as DeliveryResult.Failed).reason, backoff)
            sleep(backoff)
            backoff = backoff.multipliedBy(2)
        }
    }

    fun stats() = DeliveryStats(delivered.get(), failed.get(), retries.get())

    /** Tells every plugin the application is stopping. */
    fun stop() = byId.values.forEach { handle ->
        runCatching { handle.plugin.stop() }.onFailure { log.warn("delivery plugin {} failed to stop", handle.id, it) }
    }

    private fun attemptOnce(plugin: DeliveryPlugin, message: NotificationMessage, target: DeliveryTarget): DeliveryResult = try {
        plugin.deliver(message, target)
    } catch (e: Exception) {
        log.warn("{} threw while delivering '{}'", plugin.id, message.title, e)
        DeliveryResult.Failed("${plugin.name} failed: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun problems(field: PluginField, value: String?): List<FieldProblem> {
        if (value.isNullOrBlank() || (field.type == FieldType.CHECKBOX && value == "false")) {
            return if (field.required) listOf(FieldProblem(field.key, "is required")) else emptyList()
        }
        return when (field.type) {
            FieldType.SELECT -> if (field.options.none { it.value == value }) listOf(FieldProblem(field.key, "must be one of the choices")) else emptyList()
            FieldType.NUMBER -> if (value.toBigDecimalOrNull() == null) listOf(FieldProblem(field.key, "must be a number")) else emptyList()
            FieldType.URL -> if (!URL.matches(value)) listOf(FieldProblem(field.key, "must be an http or https URL")) else emptyList()
            else -> emptyList()
        }
    }

    private fun secretKeys(pluginId: String): Set<String> =
        byId[pluginId]?.plugin?.fields?.filter { it.secret }?.map { it.key }?.toSet().orEmpty()

    private fun describe(handle: PluginHandle): PluginDescriptor {
        val status = handle.status()
        val plugin = handle.plugin
        return PluginDescriptor(
            plugin.id, plugin.name, plugin.description, plugin.icon, plugin.fields,
            status.ready, status.reason, handle.source, datafill.settingsFor(plugin.id).keys.sorted(),
        )
    }

    companion object {
        /** What a stored secret is shown as. Sent back unchanged, it keeps the stored value. */
        const val MASK = "********"

        private val URL = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    }
}
