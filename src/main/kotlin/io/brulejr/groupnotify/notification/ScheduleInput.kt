/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A schedule as submitted, before it is checked by [ScheduleRules]. Times are local to
 * [zone], as an author thinks of them.
 *
 * @property zone an IANA zone such as `America/New_York`; blank for the default.
 * @property runAt a one-off's time.
 * @property until a recurring schedule's last day, inclusive.
 * @property pattern a pattern schedule in words; see [PatternParser].
 */
data class ScheduleInput(
    val kind: ScheduleKind,
    val zone: String = "",
    val runAt: LocalDateTime? = null,
    val cron: String = "",
    val until: LocalDate? = null,
    val pattern: String = "",
)
