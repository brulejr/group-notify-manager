/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

/** One thing wrong with an instance draft; a plugin field is named `config.<key>`. */
data class InstanceProblem(val field: String, val reason: String)
