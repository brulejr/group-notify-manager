/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.email

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.plugin.FieldType
import io.brulejr.groupnotify.plugin.MailMessage
import io.brulejr.groupnotify.plugin.Mailer
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginField
import io.brulejr.groupnotify.plugin.PluginStatus

/**
 * Ordinary email, through the application's SMTP connection (`spring.mail.*`). Sent as
 * HTML with a plain-text alternative.
 *
 * Has no settings of its own: the sender is `notify.mail.from`.
 */
class EmailPlugin : DeliveryPlugin {

    private var mailer: Mailer? = null

    override val id = "email"
    override val name = "Email"
    override val description = "Sends an email to a list of addresses, through the application's SMTP server."
    override val icon = "envelope"

    override val fields = listOf(
        PluginField("to", "To", FieldType.EMAIL, required = true, placeholder = "someone@example.org, …"),
        PluginField("cc", "Cc", FieldType.EMAIL),
        PluginField("bcc", "Bcc", FieldType.EMAIL, help = "For a list whose members should not see one another."),
        PluginField("replyTo", "Reply to", FieldType.EMAIL),
        PluginField("subjectPrefix", "Subject prefix", placeholder = "e.g. [Club]"),
    )

    override fun start(context: PluginContext) {
        mailer = context.mailer
    }

    override fun status() =
        if (mailer == null) PluginStatus.notReady("no SMTP server: set spring.mail.host") else PluginStatus.ready()

    override fun validate(target: DeliveryTarget): List<FieldProblem> =
        listOf("to", "cc", "bcc", "replyTo").flatMap { key ->
            target.list(key).filterNot { Addresses.valid(it) }.map { FieldProblem(key, "$it is not an email address") }
        }

    override fun summarize(target: DeliveryTarget): String {
        val count = listOf("to", "cc", "bcc").sumOf { target.list(it).size }
        val to = target.list("to")
        return if (count == 1) to.single() else "$count addresses"
    }

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        val mailer = this.mailer ?: return DeliveryResult.Failed("no SMTP server is configured")
        val subject = listOfNotNull(target["subjectPrefix"], message.subject).joinToString(" ")
        return try {
            mailer.send(
                MailMessage(
                    to = target.list("to"),
                    cc = target.list("cc"),
                    bcc = target.list("bcc"),
                    replyTo = target["replyTo"],
                    subject = subject,
                    text = message.text,
                    html = message.html,
                ),
            )
            DeliveryResult.Delivered("${listOf("to", "cc", "bcc").sumOf { target.list(it).size }} recipients")
        } catch (e: Exception) {
            // Usually the server being away or refusing to relay; either may pass.
            DeliveryResult.Failed("SMTP: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        }
    }
}
