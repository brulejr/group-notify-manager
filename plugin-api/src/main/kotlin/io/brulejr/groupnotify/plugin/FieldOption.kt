/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/** One choice of a [FieldType.SELECT] field: [value] is stored, [label] is shown. */
data class FieldOption(val value: String, val label: String = value)
