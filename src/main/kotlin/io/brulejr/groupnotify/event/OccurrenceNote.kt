/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

import io.brulejr.groupnotify.notification.BodyFormat

/**
 * One occurrence's own words, e.g. "This week: guest speaker", written in either editor
 * like a body. Added to that occurrence's messages, converted to each message's format.
 */
data class OccurrenceNote(val text: String, val format: BodyFormat = BodyFormat.MARKDOWN)
