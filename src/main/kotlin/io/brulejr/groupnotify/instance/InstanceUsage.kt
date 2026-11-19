/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import java.util.UUID

/**
 * Which notifications or events send through an instance, so one in use is not deleted
 * from under them. Provided by the notification and event features, which depend on
 * this one rather than the other way round; every one is asked.
 */
fun interface InstanceUsage {

    /** The titles of what has a delivery through [instanceId]. */
    fun notificationsUsing(instanceId: UUID): List<String>
}
