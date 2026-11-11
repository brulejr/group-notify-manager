/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.Mailer

/** The SMTP connection lent to plugins, or none: a bean cannot be null, so it is wrapped. */
data class PluginMailer(val mailer: Mailer?)
