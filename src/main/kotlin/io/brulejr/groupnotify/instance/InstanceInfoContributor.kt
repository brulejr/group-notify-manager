/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor

/** Where instances are kept and whether their secrets have a lasting key; never the key. */
class InstanceInfoContributor(private val datafill: InstanceDatafill, private val cipher: SecretCipher) : InfoContributor {
    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "instances",
            mapOf(
                "store" to datafill.store.name.lowercase(),
                "secretKey" to if (cipher.ephemeral) "ephemeral" else "configured",
                "encryption" to "AES-256-GCM",
            ),
        )
    }
}
