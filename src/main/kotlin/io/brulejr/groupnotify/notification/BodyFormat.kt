/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.notification

/** How a body is written, and so which editor the console opens it in. */
enum class BodyFormat(val label: String) {
    MARKDOWN("Markdown"),

    /** Written in the WYSIWYG editor. */
    HTML("Rich text"),
}
