/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.plugins.email

/**
 * A deliberately loose address check: something@domain.tld, no spaces. The SMTP server
 * is the real judge; this only catches a pasted name or a missing @.
 */
object Addresses {
    private val ADDRESS = Regex("[^@\\s,;<>]+@[^@\\s,;<>]+\\.[^@\\s,;<>]+")

    fun valid(address: String) = ADDRESS.matches(address)
}
