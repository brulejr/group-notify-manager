/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

/** A stored secret cannot be opened: sealed under another key, or altered. Re-enter it. */
class SecretUnreadableException(cause: Throwable) : RuntimeException("a stored secret cannot be decrypted", cause)
