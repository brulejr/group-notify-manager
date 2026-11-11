/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.audit

import io.brulejr.groupnotify.platform.StoreType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property store where the history is kept.
 * @property retention how long an event is kept. Older ones are deleted as new ones are
 *   recorded, at most once an hour.
 */
@ConfigurationProperties("notify.audit")
data class AuditDatafill(
    val store: StoreType = StoreType.POSTGRES,
    val retention: Duration = Duration.ofDays(365),
) {
    init {
        require(retention >= Duration.ofDays(1)) { "notify.audit.retention must be at least 1d" }
    }
}
