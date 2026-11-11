/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.platform

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import javax.sql.DataSource

/**
 * Brings the application's PostgreSQL schema up to date, the first time anything needs it.
 *
 * Flyway is run here rather than through Spring Boot's auto-configuration, which migrates
 * at startup unconditionally: a context with every store in memory would then still need
 * a database. Each feature that chooses PostgreSQL calls [migrate] while its beans are
 * created, so nothing connects unless something uses it.
 *
 * That also means startup fails when PostgreSQL is chosen but cannot be reached, which is
 * deliberate: a scheduler that cannot read its schedule must not look healthy.
 */
class DatabaseMigrator(
    private val dataSource: DataSource,
    private val dataSourceProperties: DataSourceProperties,
    private val datafill: DatabaseDatafill,
) {

    private val log = LoggerFactory.getLogger(DatabaseMigrator::class.java)

    // Lazy, and so synchronised: whichever feature asks first runs the migration, and the
    // rest find it done.
    private val migration = lazy {
        requireResolvedDataSource(dataSourceProperties)
        val result = Flyway.configure()
            .dataSource(dataSource)
            .schemas(datafill.schema)
            .defaultSchema(datafill.schema)
            .locations(LOCATION)
            .load()
            .migrate()
        log.info(
            "PostgreSQL schema '{}' is at version {} ({} migration(s) applied)",
            datafill.schema, result.targetSchemaVersion ?: result.initialSchemaVersion, result.migrationsExecuted,
        )
    }

    /** Runs any pending migrations, once per process. */
    fun migrate() = migration.value

    /** Whether any feature has chosen PostgreSQL; with every store in memory it is never reached. */
    val inUse: Boolean get() = migration.isInitialized()

    private companion object {
        const val LOCATION = "classpath:db/migration"
    }
}
