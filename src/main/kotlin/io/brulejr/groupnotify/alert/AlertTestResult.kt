/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert

/** What a test alert came to. */
sealed interface AlertTestResult {
    data class Sent(val instance: String, val detail: String) : AlertTestResult
    data class Failed(val instance: String, val reason: String) : AlertTestResult
    data object NotFound : AlertTestResult
    data object Forbidden : AlertTestResult
}
