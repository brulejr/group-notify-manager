/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.alert.api

/** A test alert's result, as the REST API reports it. */
data class AlertTestView(val sent: Boolean, val instance: String, val detail: String)
