/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.event.web

/** One property's inputs in the form, with any problem against each. */
data class PropertyRow(val index: Int, val name: String, val value: String, val nameError: String?, val valueError: String?)
