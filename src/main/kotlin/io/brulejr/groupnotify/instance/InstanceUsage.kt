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
 * Which notifications send through an instance, so one in use is not deleted from under
 * them. Provided by the notification feature, which depends on this one rather than the
 * other way round.
 */
fun interface InstanceUsage {

    /** The titles of the notifications with a delivery through [instanceId]. */
    fun notificationsUsing(instanceId: UUID): List<String>
}
