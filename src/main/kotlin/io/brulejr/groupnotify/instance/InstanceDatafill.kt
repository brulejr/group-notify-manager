/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import io.brulejr.groupnotify.platform.StoreType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property store where instances are kept.
 * @property secretKey the AES-256 key instance secrets are sealed with, 32 bytes in
 *   base64 (`openssl rand -base64 32`), from `NOTIFY_SECRET_KEY`. Blank makes a key for
 *   this run only; see [SecretCipher].
 * @property maxNameLength of an instance's name.
 */
@ConfigurationProperties("notify.instances")
data class InstanceDatafill(
    val store: StoreType = StoreType.POSTGRES,
    val secretKey: String = "",
    val maxNameLength: Int = 80,
) {
    init {
        if (secretKey.isNotBlank()) {
            SecretCipher.problemWith(secretKey)?.let { throw IllegalArgumentException("notify.instances.secret-key $it") }
        }
        require(maxNameLength in 10..200) { "notify.instances.max-name-length must be between 10 and 200" }
    }
}
