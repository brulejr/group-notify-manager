/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.dispatch

import io.brulejr.groupnotify.audit.AuditEvent
import io.brulejr.groupnotify.audit.AuditEventType
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.audit.DispatchTrigger
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.delivery.DeliveryOutcome
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.SecretUnreadableException
import io.brulejr.groupnotify.notification.Notification
import io.brulejr.groupnotify.notification.NotificationDelivery
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.plugin.DeliveryResult
import io.brulejr.groupnotify.security.NotifyUser
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Sends notifications: on schedule, when [runDue] finds them due, and on demand.
 *
 * A due notification is moved on to its next run *before* it is sent. If the
 * application stops partway, that run is not sent twice on restart; it may not finish
 * either, which the audit trail shows. Moving it on is conditional on nobody having
 * paused or rescheduled it since it was read, so an edit always wins over a run in
 * flight.
 */
class DispatchService(
    private val notifications: NotificationService,
    private val deliveries: DeliveryService,
    private val instances: InstanceService,
    private val content: ContentService,
    private val audit: AuditService,
    private val groups: GroupService,
    private val datafill: DispatchDatafill,
    private val clock: Clock,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(DispatchService::class.java)
    private val workers: ExecutorService = Executors.newFixedThreadPool(datafill.threads) { task ->
        Thread(task, "dispatch-worker").apply { isDaemon = true }
    }

    private val polls = AtomicLong()
    private val runs = AtomicLong()
    private val skipped = AtomicLong()
    private val sent = AtomicLong()
    private val failed = AtomicLong()
    private val lastPollAt = AtomicReference<Instant?>()
    private val lastError = AtomicReference<String?>()

    /**
     * Sends everything due now, a batch at a time, and returns how many were taken.
     * Called by the poller; safe to call from elsewhere, since a notification another
     * call has already moved on is left alone.
     */
    fun runDue(): Int {
        val now = clock.instant()
        polls.incrementAndGet()
        lastPollAt.set(now)
        return try {
            val due = notifications.due(now, datafill.batchSize)
            workers.invokeAll(due.map { notification -> Callable { runSafely(notification, now) } }).forEach { it.get() }
            lastError.set(null)
            due.size
        } catch (e: Exception) {
            lastError.set(e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    /**
     * Sends a notification now, outside its schedule, which it leaves alone. With
     * [deliveryId], only that delivery: a test of one channel.
     */
    fun sendNow(id: UUID, user: NotifyUser, deliveryId: UUID? = null): SendResult {
        val notification = notifications.find(id, user) ?: return SendResult.NotFound
        if (!notifications.canEdit(user, notification)) return SendResult.Forbidden
        val chosen = notification.deliveries.filter { if (deliveryId != null) it.id == deliveryId else it.enabled }
        if (chosen.isEmpty()) return SendResult.NothingToSend
        return SendResult.Sent(deliver(notification, chosen, clock.instant(), DispatchTrigger.MANUAL, user.actor))
    }

    fun stats() = DispatchStats(polls.get(), runs.get(), skipped.get(), sent.get(), failed.get(), lastPollAt.get(), lastError.get())

    override fun close() {
        workers.shutdown()
    }

    private fun runSafely(notification: Notification, now: Instant) = try {
        run(notification, now)
    } catch (e: Exception) {
        log.error("dispatching '{}' ({}) failed", notification.title, notification.id, e)
    }

    private fun run(notification: Notification, now: Instant) {
        val dueAt = checkNotNull(notification.nextRunAt)
        // From now rather than from when it was due, so a notification that was missed
        // several times is sent once, not once per missed run.
        val next = notification.schedule.nextAfter(maxOf(now, dueAt))
        if (!notifications.advance(notification, next, now)) {
            log.debug("'{}' changed while being dispatched; leaving it", notification.title)
            return
        }
        runs.incrementAndGet()
        val late = Duration.between(dueAt, now)
        if (late > datafill.misfireGrace) {
            skipped.incrementAndGet()
            record(notification, AuditEventType.SKIPPED, AuditEvent.SCHEDULER, DispatchTrigger.SCHEDULE,
                "due at $dueAt, missed by ${late.toMinutes()} min (grace ${datafill.misfireGrace.toMinutes()} min)")
        } else {
            deliver(notification, notification.deliveries.filter { it.enabled }, dueAt, DispatchTrigger.SCHEDULE, AuditEvent.SCHEDULER)
        }
        if (next == null) record(notification, AuditEventType.COMPLETED, AuditEvent.SCHEDULER, DispatchTrigger.SCHEDULE, "no runs left")
    }

    private fun deliver(
        notification: Notification,
        chosen: List<NotificationDelivery>,
        at: Instant,
        trigger: DispatchTrigger,
        actor: String,
    ): List<DeliveryReport> {
        val message = content.render(notification, groups.displayName(notification.group), at)
        return chosen.map { delivery ->
            val instance = instances.get(delivery.instance)
            val (outcome, summary) = when {
                instance == null -> refused("its delivery instance was deleted") to "deleted instance"
                // Checked when saved; checked again in case the notification moved group since.
                instance.group != notification.group -> refused("${instance.name} belongs to another group") to instance.name
                else -> {
                    val target = try {
                        instances.target(instance, delivery.overrides)
                    } catch (e: SecretUnreadableException) {
                        null
                    }
                    (target?.let { deliveries.deliver(instance.plugin, message, it) }
                        ?: refused("a secret of ${instance.name} cannot be decrypted; an admin must re-enter it")) to
                        "${instance.name}: ${instances.summary(instance)}"
                }
            }
            val plugin = instance?.plugin ?: "unknown"
            val (type, detail, reference) = when (val result = outcome.result) {
                is DeliveryResult.Delivered -> Triple(AuditEventType.SENT, result.detail, result.reference)
                is DeliveryResult.Failed -> Triple(AuditEventType.FAILED, result.reason, null)
            }
            (if (outcome.delivered) sent else failed).incrementAndGet()
            if (!outcome.delivered) log.warn("'{}' via {} ({}) failed: {}", notification.title, plugin, summary, detail)
            audit.record(
                AuditEvent(
                    occurredAt = clock.instant(),
                    type = type,
                    actor = actor,
                    notificationId = notification.id,
                    notificationTitle = notification.title,
                    group = notification.group,
                    deliveryId = delivery.id,
                    plugin = plugin,
                    target = summary,
                    trigger = trigger,
                    detail = detail,
                    reference = reference,
                    attempts = outcome.attempts,
                ),
            )
            DeliveryReport(delivery, instance?.name ?: "a deleted instance", summary, outcome)
        }
    }

    /** A delivery that never reached its plugin. */
    private fun refused(reason: String) = DeliveryOutcome(DeliveryResult.Failed(reason), 0)

    private fun record(notification: Notification, type: AuditEventType, actor: String, trigger: DispatchTrigger, detail: String) {
        audit.record(
            AuditEvent(
                occurredAt = clock.instant(),
                type = type,
                actor = actor,
                notificationId = notification.id,
                notificationTitle = notification.title,
                group = notification.group,
                trigger = trigger,
                detail = detail,
            ),
        )
    }
}
