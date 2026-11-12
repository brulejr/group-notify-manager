/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

/** Outcome of changing an instance. */
sealed interface InstanceResult {

    data class Saved(val instance: PluginInstance) : InstanceResult

    /** Refused, with every problem found; nothing was written. */
    data class Invalid(val problems: List<InstanceProblem>) : InstanceResult

    /** No such instance, or not one the user may see. */
    data object NotFound : InstanceResult

    /** Only admins provision instances. */
    data object Forbidden : InstanceResult

    /** Not deleted: these notifications, by title, still send through it. */
    data class InUse(val notifications: List<String>) : InstanceResult
}
