/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.support

import io.brulejr.groupnotify.audit.AuditDatafill
import io.brulejr.groupnotify.audit.AuditService
import io.brulejr.groupnotify.audit.InMemoryAuditStore
import io.brulejr.groupnotify.content.ContentDatafill
import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.delivery.DeliveryDatafill
import io.brulejr.groupnotify.delivery.DeliveryService
import io.brulejr.groupnotify.delivery.PluginHandle
import io.brulejr.groupnotify.dispatch.DispatchDatafill
import io.brulejr.groupnotify.dispatch.DispatchService
import io.brulejr.groupnotify.group.GroupDatafill
import io.brulejr.groupnotify.group.GroupService
import io.brulejr.groupnotify.notification.BodyFormat
import io.brulejr.groupnotify.notification.InMemoryNotificationStore
import io.brulejr.groupnotify.notification.NotificationDatafill
import io.brulejr.groupnotify.notification.NotificationDraft
import io.brulejr.groupnotify.notification.NotificationService
import io.brulejr.groupnotify.notification.ScheduleKind
import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.platform.StoreType
import java.time.Duration
import java.time.LocalDateTime

/** The services wired together without Spring, on a clock the test controls. */
class Fixtures(
    vararg plugins: DeliveryPlugin = arrayOf(RecordingPlugin()),
    val clock: TestClock = TestClock(),
    deliveryDatafill: DeliveryDatafill = DeliveryDatafill(retryBackoff = Duration.ZERO),
    dispatchDatafill: DispatchDatafill = DispatchDatafill(threads = 1),
) {
    val groups = GroupService(GroupDatafill(pattern = "notify_.*"))
    val auditStore = InMemoryAuditStore()
    val audit = AuditService(auditStore, groups, AuditDatafill(store = StoreType.MEMORY), clock)
    val deliveries = DeliveryService(plugins.map { PluginHandle(it, "test") }, deliveryDatafill) { }
    val notificationStore = InMemoryNotificationStore()
    val notifications = NotificationService(
        notificationStore, groups, deliveries, audit, NotificationDatafill(store = StoreType.MEMORY, defaultZone = "America/New_York"), clock,
    )
    val content = ContentService(ContentDatafill())
    val dispatch = DispatchService(notifications, deliveries, content, audit, groups, dispatchDatafill, clock)

    /** A club notification sent once, a day from now, through the recording plugin. */
    fun draft(
        group: String = "notify_club",
        kind: ScheduleKind = ScheduleKind.ONCE,
        runAt: LocalDateTime? = LocalDateTime.of(2026, 11, 11, 18, 0),
        cron: String = "",
        deliveries: List<NotificationDraft.Delivery> = listOf(NotificationDraft.Delivery(plugin = "recording", target = mapOf("channel" to "general"))),
    ) = NotificationDraft(
        group = group,
        title = "Meeting reminder",
        subject = "Meeting on {{weekday}}",
        body = "See you at **{{time}}**.",
        format = BodyFormat.MARKDOWN,
        kind = kind,
        zone = "America/New_York",
        runAt = runAt,
        cron = cron,
        deliveries = deliveries,
    )
}
