/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event

/**
 * One of an event's characteristics, e.g. `location` → `Town hall`, which its messages
 * use as `{{event.location}}`.
 *
 * @property name lower-case letters, digits and underscores, starting with a letter.
 */
data class EventProperty(val name: String, val value: String)
