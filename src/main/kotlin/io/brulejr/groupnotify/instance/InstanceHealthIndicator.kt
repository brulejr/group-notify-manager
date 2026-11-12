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
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * How many instances there are, and whether their secrets can be kept: down when they
 * are stored in PostgreSQL under a key made for this run, which a restart loses.
 */
class InstanceHealthIndicator(
    private val store: InstancePort,
    private val cipher: SecretCipher,
    private val datafill: InstanceDatafill,
) : HealthIndicator {

    override fun health(): Health {
        val instances = store.list(null)
        val builder = if (cipher.ephemeral && datafill.store == StoreType.POSTGRES && instances.isNotEmpty()) Health.down() else Health.up()
        return builder
            .withDetail("instances", instances.size)
            .withDetail("groups", instances.map { it.group }.distinct().size)
            .withDetail("secretKey", if (cipher.ephemeral) "not set: secrets are lost on restart (set NOTIFY_SECRET_KEY)" else "set")
            .build()
    }
}
