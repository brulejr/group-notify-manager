/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.platform

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.jdbc.core.simple.JdbcClient
import javax.sql.DataSource

/**
 * PostgreSQL's health, checked only when a store uses it.
 *
 * Replaces Spring Boot's own `db` indicator, which connects whatever the stores are:
 * with every store in memory it would report the application down for a database
 * nothing needs, and log a connection failure on every health check.
 */
class DatabaseHealthIndicator(
    private val migrator: DatabaseMigrator,
    private val dataSource: DataSource,
) : HealthIndicator {

    override fun health(): Health {
        if (!migrator.inUse) return Health.up().withDetail("database", "not used: every store is in memory").build()
        return try {
            JdbcClient.create(dataSource).sql("SELECT 1").query(Int::class.java).single()
            Health.up().withDetail("database", "PostgreSQL").build()
        } catch (e: Exception) {
            Health.down(e).build()
        }
    }
}
