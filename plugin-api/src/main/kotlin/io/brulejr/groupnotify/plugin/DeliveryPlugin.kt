/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/**
 * A way of delivering a notification: Discord, SMS, an email list, or anything else a
 * plugin jar provides, such as a packet radio gateway.
 *
 * The application finds plugins with [java.util.ServiceLoader], so a plugin is a jar
 * with a `META-INF/services/io.brulejr.groupnotify.plugin.DeliveryPlugin` file naming
 * its implementation, on the classpath or in the plugins directory. It needs a public
 * no-argument constructor.
 *
 * A plugin is told two kinds of configuration apart:
 * - **settings**, fixed per installation and kept out of the database: API keys, a
 *   sending number, a gateway address. They come from `notify.delivery.settings.<id>.*`
 *   and reach the plugin once, through [start].
 * - **a target**, one per delivery of a notification: which channel, which numbers,
 *   which list. The console builds the form for it from [fields], and the application
 *   stores it with the notification.
 *
 * Calls to [deliver] may come from several threads at once.
 */
interface DeliveryPlugin {

    /**
     * Stable identifier, stored with every delivery that uses this plugin, e.g. `discord`.
     * Lower case letters, digits and dashes. Changing it orphans existing deliveries.
     */
    val id: String

    /** What the console calls it, e.g. `Discord`. */
    val name: String

    /** One sentence for the console's plugin list. */
    val description: String

    /** A Bootstrap Icons name, without the `bi-` prefix, e.g. `discord`. */
    val icon: String get() = "send"

    /** What one delivery needs to say, in the order the console shows them. */
    val fields: List<PluginField>

    /**
     * Called once, before any delivery, with this installation's settings and the
     * services the application lends plugins. A plugin that is missing a setting should
     * remember why and report it from [status] rather than throw: an unconfigured plugin
     * must not stop the application from starting.
     */
    fun start(context: PluginContext) {}

    /** Whether the plugin can deliver right now, and if not, why. */
    fun status(): PluginStatus = PluginStatus.ready()

    /**
     * Problems with a target beyond those the application checks itself, which are
     * required fields and the options of a [FieldType.SELECT]. Keyed by field key.
     */
    fun validate(target: DeliveryTarget): List<FieldProblem> = emptyList()

    /**
     * A short description of a target for lists and the audit trail, e.g. `#announcements`
     * or `3 numbers`. It is shown to every member of the group, so it must never include a
     * [PluginField.secret] value.
     */
    fun summarize(target: DeliveryTarget): String

    /**
     * Sends one notification to one target. Report failure as [DeliveryResult.Failed]
     * rather than by throwing; an exception is recorded as a failure too, but with less
     * to say about it.
     */
    fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult

    /** Called once when the application stops. */
    fun stop() {}
}
