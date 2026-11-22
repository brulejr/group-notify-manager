/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditListener
import io.brulejr.groupnotify.audit.AuditQuery
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.delivery.DeliveryOutcome
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.instance.SecretUnreadableException
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.plugin.NotificationMessage
import io.brulejr.groupnotify.security.NotifyUser
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Posts a group's failures to the instances it marked **Send alerts here**: a scheduled
 * delivery that failed after its retries, a run skipped for being late, and, once a
 * failing instance sends again, that it has recovered. Told by the audit trail as each
 * event is recorded, it records each alert there too, as [AuditEventType.ALERTED] or
 * [AuditEventType.RECOVERED], and decides from those records, so a restart forgets nothing.
 *
 * Kept quiet: one alert per failing instance (or per message's skipped runs) every
 * `quiet-period`, the failures in between counted into the next; never through the
 * instance that failed; and nothing about an alert that could not be sent but its record.
 * Only scheduled sends are alerted: whoever pressed Send now or Test sees the result.
 */
class AlertService(
    private val audit: AuditService,
    private val instances: InstanceService,
    private val deliveries: DeliveryService,
    private val content: ContentService,
    private val groups: GroupService,
    private val datafill: AlertDatafill,
    private val clock: Clock,
) : AuditListener {

    @Synchronized
    override fun recorded(event: AuditEvent) {
        if (!datafill.enabled) return
        when (event.type) {
            AuditEventType.FAILED -> if (event.trigger == DispatchTrigger.SCHEDULE) failed(event)
            AuditEventType.SKIPPED -> if (datafill.skipped && event.trigger == DispatchTrigger.SCHEDULE) skipped(event)
            AuditEventType.SENT -> if (datafill.recovery && event.instanceId != null) sent(event, event.instanceId)
            else -> Unit
        }
    }

    /** Sends a test alert through [instanceId], for someone who manages its group. */
    fun test(instanceId: UUID, user: NotifyUser): AlertTestResult {
        val instance = instances.find(instanceId, user) ?: return AlertTestResult.NotFound
        if (!instances.canManage(user, instance.group)) return AlertTestResult.Forbidden
        val groupName = groups.displayName(instance.group)
        val outcome = send(
            instance,
            message(
                instance.group, "Alert test", null,
                "Test alert for $groupName",
                "This is a test alert from Group Notify for **${md(groupName)}**. When one of the group's scheduled messages or " +
                    "event reminders fails to go out, it is posted here.",
                icon = "🔔",
            ),
        )
        audit.record(
            AuditEvent(
                occurredAt = clock.instant(), type = AuditEventType.ALERTED, actor = user.actor, notificationId = null,
                notificationTitle = "Delivery instance: ${instance.name}", group = instance.group, plugin = instance.plugin,
                target = instance.name, trigger = DispatchTrigger.MANUAL, attempts = outcome.attempts,
                detail = if (outcome.delivered) "test alert sent" else "test alert not sent: ${outcome.detail}",
            ),
        )
        return if (outcome.delivered) AlertTestResult.Sent(instance.name, outcome.detail) else AlertTestResult.Failed(instance.name, outcome.detail)
    }

    /** How many alert instances there are, and alerts were posted since [since]; for health. */
    fun counts(since: Instant): Pair<Int, Long> =
        instances.alertTargets(null).size to (audit.count(AuditQuery(types = setOf(AuditEventType.ALERTED, AuditEventType.RECOVERED), since = since)).values.sum())

    /** The groups with instances but none marked for alerts; for health. */
    fun groupsWithoutAlerts(): Set<String> = instances.groupsInUse() - instances.alertTargets(null).map { it.group }.toSet()

    // ---- what is alerted ----

    private fun failed(event: AuditEvent) {
        val instanceId = event.instanceId ?: return bySubject(event, failure(event, "a deleted instance", 0))
        val previous = lastAbout(instanceId)
        val now = clock.instant()
        if (previous?.type == AuditEventType.ALERTED && Duration.between(previous.occurredAt, now) < datafill.quietPeriod) return
        // The failures through it after the last alert, besides this one, held back meanwhile. The
        // one that raised that alert came before it; a microsecond on, as PostgreSQL keeps time.
        val more = if (previous?.type == AuditEventType.ALERTED) {
            val after = previous.occurredAt.plusNanos(1_000)
            ((audit.count(AuditQuery(instanceId = instanceId, types = setOf(AuditEventType.FAILED), since = after))[AuditEventType.FAILED] ?: 1L) - 1).coerceAtLeast(0)
        } else 0L
        val name = instances.get(instanceId)?.name ?: event.target?.substringBefore(": ") ?: "its delivery instance"
        post(event, AuditEventType.ALERTED, instanceId, failure(event, name, more))
    }

    private fun skipped(event: AuditEvent) {
        val title = md(event.notificationTitle)
        bySubject(
            event,
            message(event.group, event.notificationTitle, link(event), "${event.notificationTitle} was skipped", "**$title** was not sent: ${md(event.detail)}."),
        )
    }

    private fun sent(event: AuditEvent, instanceId: UUID) {
        if (lastAbout(instanceId)?.type != AuditEventType.ALERTED) return
        val name = instances.get(instanceId)?.name ?: event.target?.substringBefore(": ") ?: "The delivery instance"
        post(
            event, AuditEventType.RECOVERED, instanceId,
            message(
                event.group, event.notificationTitle, link(event), "$name is sending again",
                "*${md(name)}* is sending again: **${md(event.notificationTitle)}** went out.",
                icon = "✅",
            ),
        )
    }

    /** For what has no instance to key on (a skipped run, a deleted instance): kept quiet per message or event. */
    private fun bySubject(event: AuditEvent, message: NotificationMessage) {
        if (event.notificationId == null && event.eventId == null) return
        val since = clock.instant().minus(datafill.quietPeriod)
        val recent = audit.find(
            AuditQuery(notificationId = event.notificationId, eventId = event.eventId, types = setOf(AuditEventType.ALERTED), since = since, limit = 20),
        ).any { it.instanceId == null && it.actor == AuditEvent.SCHEDULER }
        if (!recent) post(event, AuditEventType.ALERTED, null, message)
    }

    /** The latest alert or recovery about [instanceId], telling whether it is thought to be failing. */
    private fun lastAbout(instanceId: UUID): AuditEvent? =
        audit.find(AuditQuery(instanceId = instanceId, types = setOf(AuditEventType.ALERTED, AuditEventType.RECOVERED), limit = 1)).firstOrNull()

    private fun failure(event: AuditEvent, instanceName: String, more: Long): NotificationMessage {
        val attempts = event.attempts?.takeIf { it > 0 }?.let { " ($it attempt${if (it == 1) "" else "s"})" }.orEmpty()
        val body = buildString {
            append("**${md(event.notificationTitle)}** didn't go out through *${md(instanceName)}*: ${md(event.detail)}$attempts.")
            if (more > 0) append("\n\n$more more failure${if (more == 1L) "" else "s"} through ${md(instanceName)} since the last alert.")
        }
        return message(event.group, event.notificationTitle, link(event), "${event.notificationTitle} didn't go out", body)
    }

    // ---- sending ----

    /** Posts [message] to [source]'s group's alerts instances, but the one that failed, recording each. */
    private fun post(source: AuditEvent, type: AuditEventType, about: UUID?, message: NotificationMessage) {
        instances.alertTargets(source.group).filter { it.id != about }.forEach { target ->
            val outcome = send(target, message)
            audit.record(
                AuditEvent(
                    occurredAt = clock.instant(), type = type, actor = AuditEvent.SCHEDULER, notificationId = source.notificationId,
                    notificationTitle = source.notificationTitle, group = source.group, plugin = target.plugin, target = target.name,
                    trigger = DispatchTrigger.SCHEDULE, attempts = outcome.attempts, eventId = source.eventId, instanceId = about,
                    reference = (outcome.result as? DeliveryResult.Delivered)?.reference,
                    detail = message.subject + if (outcome.delivered) "" else "; not sent: ${outcome.detail}",
                ),
            )
        }
    }

    private fun send(instance: PluginInstance, message: NotificationMessage): DeliveryOutcome = try {
        deliveries.deliver(instance.plugin, message, instances.target(instance, emptyMap()))
    } catch (e: SecretUnreadableException) {
        DeliveryOutcome(DeliveryResult.Failed("a secret of ${instance.name} cannot be decrypted; it must be re-entered"), 0)
    }

    private fun message(group: String, title: String, link: String?, subject: String, markdown: String, icon: String = "⚠"): NotificationMessage {
        val body = markdown + (link?.let { "\n\n$it" } ?: "") + "\n\n— ${md(groups.displayName(group))}"
        return NotificationMessage(
            notificationId = "alert", title = "Alert: $title", group = group, subject = "$icon $subject",
            markdown = body, html = content.html(body, BodyFormat.MARKDOWN), text = content.text(body, BodyFormat.MARKDOWN), scheduledFor = clock.instant(),
        )
    }

    /** Where the message or event is in the console, when its address is known. */
    private fun link(event: AuditEvent): String? {
        val base = datafill.consoleUrl.trimEnd('/').takeIf { it.isNotBlank() } ?: return null
        return when {
            event.notificationId != null -> "$base/ui/messages/${event.notificationId}"
            event.eventId != null -> "$base/ui/events/${event.eventId}"
            else -> null
        }
    }

    /** [text] with Markdown's markup characters escaped, so a title is shown as written. */
    private fun md(text: String) = text.replace(Regex("""([\\`*_\[\]<>#|~])"""), """\\$1""")
}
