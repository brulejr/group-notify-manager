/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.groupsio

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.DeliveryTarget
import io.brulejr.groupnotify.plugin.FieldProblem
import io.brulejr.groupnotify.plugin.MailMessage
import io.brulejr.groupnotify.plugin.Mailer
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.plugin.PluginContext
import io.brulejr.groupnotify.plugin.PluginField
import io.brulejr.groupnotify.plugin.PluginStatus

/**
 * Posts to a groups.io group or subgroup by emailing its posting address,
 * `<group>@groups.io` or `<group>+<subgroup>@groups.io`, the way any member posts.
 *
 * groups.io accepts a post only from an address that is a member with permission to
 * post, so the sender (`notify.mail.from`, or the `from` setting here) must be
 * subscribed. A moderated group holds the post for approval unless that member is
 * allowed to post unmoderated. Hashtags in the subject are how groups.io files a post
 * under a topic; a group can mute or route hashtags per member.
 *
 * Settings (`notify.delivery.settings.groupsio.*`), all optional:
 * - `domain`: default `groups.io`; a group on its own domain uses that.
 * - `from`: the member address to post as, if not `notify.mail.from`.
 */
class GroupsIoPlugin : DeliveryPlugin {

    private var mailer: Mailer? = null
    private var domain = "groups.io"
    private var from: String? = null

    override val id = "groupsio"
    override val name = "groups.io"
    override val description = "Posts to a groups.io group or subgroup by email, as a member of it."
    override val icon = "people"

    override val fields = listOf(
        PluginField(
            "group", "Group", required = true, placeholder = "e.g. myclub or myclub+events",
            help = "The group's name, with +subgroup for a subgroup, or its full posting address.",
        ),
        PluginField(
            "hashtags", "Hashtags", placeholder = "e.g. #announcement, #meeting",
            help = "Added to the start of the subject. groups.io files the post under them.",
        ),
    )

    override fun start(context: PluginContext) {
        mailer = context.mailer
        context.settings["domain"]?.let { domain = it.trim().lowercase() }
        from = context.settings["from"]
    }

    override fun status() =
        if (mailer == null) PluginStatus.notReady("no SMTP server: set spring.mail.host") else PluginStatus.ready()

    override fun validate(target: DeliveryTarget): List<FieldProblem> = buildList {
        target["group"]?.let { if (!GROUP.matches(it.trim())) add(FieldProblem("group", "must be a group name such as myclub+events, or an address")) }
        hashtags(target).filterNot { HASHTAG.matches(it) }.forEach { add(FieldProblem("hashtags", "$it is not a hashtag: letters, digits, _ and - only")) }
    }

    override fun summarize(target: DeliveryTarget): String =
        address(target.require("group")) + hashtags(target).joinToString("") { " $it" }

    override fun deliver(message: NotificationMessage, target: DeliveryTarget): DeliveryResult {
        val mailer = this.mailer ?: return DeliveryResult.Failed("no SMTP server is configured")
        val address = address(target.require("group"))
        val subject = (hashtags(target) + message.subject).joinToString(" ")
        return try {
            mailer.send(MailMessage(to = listOf(address), subject = subject, text = message.text, html = message.html, from = from))
            DeliveryResult.Delivered("posted to $address")
        } catch (e: Exception) {
            DeliveryResult.Failed("SMTP: ${e.message ?: e.javaClass.simpleName}", retryable = true)
        }
    }

    /** The posting address: a bare group name gets the domain, a full address is used as is. */
    fun address(group: String): String = group.trim().let { if ('@' in it) it else "$it@$domain" }

    /** Each hashtag with its #, so `announcement` and `#announcement` are the same tag. */
    private fun hashtags(target: DeliveryTarget) = target.list("hashtags")
        .flatMap { it.split(' ') }.map { it.trim() }.filter { it.isNotEmpty() }.map { if (it.startsWith('#')) it else "#$it" }

    private companion object {
        val GROUP = Regex("[A-Za-z0-9][A-Za-z0-9._-]*(\\+[A-Za-z0-9._-]+)?(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?")
        val HASHTAG = Regex("#[A-Za-z0-9_-]+")
    }
}
