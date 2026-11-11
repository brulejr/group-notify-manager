/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

/** Why a notification was sent. */
enum class DispatchTrigger {
    /** It was due. */
    SCHEDULE,

    /** Someone pressed Send now, or a delivery's Test. */
    MANUAL,
}
