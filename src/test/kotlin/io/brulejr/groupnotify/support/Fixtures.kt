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
import io.brulejr.groupnotify.instance.InMemoryInstanceStore
import io.brulejr.groupnotify.instance.InstanceDatafill
import io.brulejr.groupnotify.instance.InstanceDraft
import io.brulejr.groupnotify.instance.InstanceResult
import io.brulejr.groupnotify.instance.InstanceService
import io.brulejr.groupnotify.instance.InstanceUsage
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.instance.SecretCipher
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
    val instanceStore = InMemoryInstanceStore()
    val cipher = SecretCipher(TEST_SECRET_KEY)
    val instances = InstanceService(
        instanceStore, deliveries, groups,
        InstanceUsage { id -> notificationStore.list(null).filter { n -> n.deliveries.any { it.instance == id } }.map { it.title } },
        audit, cipher, InstanceDatafill(store = StoreType.MEMORY), clock,
    )
    val notifications = NotificationService(
        notificationStore, groups, instances, audit, NotificationDatafill(store = StoreType.MEMORY, defaultZone = "America/New_York"), clock,
    )
    val content = ContentService(ContentDatafill())
    val dispatch = DispatchService(notifications, deliveries, instances, content, audit, groups, dispatchDatafill, clock)

    /** An instance provisioned by an admin, of the recording plugin by default, to #general in the club. */
    fun instance(
        group: String = "notify_club",
        name: String = "Club channel",
        plugin: String = "recording",
        config: Map<String, String> = mapOf("channel" to "general", "token" to "s3cret"),
    ): PluginInstance = (instances.create(InstanceDraft(group, plugin, name, config), TestUsers.admin) as InstanceResult.Saved).instance

    /** The club's channel, provisioned once and reused. */
    val club: PluginInstance by lazy { instance() }

    /** A delivery through [instance], with the notification's own [overrides]. */
    fun delivery(instance: PluginInstance = club, overrides: Map<String, String> = emptyMap(), enabled: Boolean = true) =
        NotificationDraft.Delivery(instance = instance.id.toString(), overrides = overrides, enabled = enabled)

    /** A club notification sent once, a day from now, through the club's channel. */
    fun draft(
        group: String = "notify_club",
        kind: ScheduleKind = ScheduleKind.ONCE,
        runAt: LocalDateTime? = LocalDateTime.of(2026, 11, 11, 18, 0),
        cron: String = "",
        deliveries: List<NotificationDraft.Delivery> = listOf(delivery()),
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

/** A fixed test key: 32 bytes in base64. */
private const val TEST_SECRET_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
