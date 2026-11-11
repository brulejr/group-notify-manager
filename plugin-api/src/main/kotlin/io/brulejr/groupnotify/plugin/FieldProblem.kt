/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugin

/** Something wrong with one field of a target, shown against that field in the console. */
data class FieldProblem(val field: String, val reason: String)
