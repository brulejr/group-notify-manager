/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.platform

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

/**
 * Cross-cutting wiring every feature shares: the clock that says what "now" is, and the
 * PostgreSQL schema the stores share.
 */
@Configuration
class PlatformConfig {

    /** One clock for every feature, so tests can stand still in time. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /**
     * Connects nothing by being created: features that choose PostgreSQL call
     * [DatabaseMigrator.migrate], and only then is the database reached.
     */
    @Bean
    fun databaseMigrator(
        dataSource: DataSource,
        dataSourceProperties: DataSourceProperties,
        datafill: DatabaseDatafill,
    ) = DatabaseMigrator(dataSource, dataSourceProperties, datafill)

    /** In place of Spring Boot's `db` indicator, which application.yml turns off. */
    @Bean
    fun databaseHealthIndicator(migrator: DatabaseMigrator, dataSource: DataSource) = DatabaseHealthIndicator(migrator, dataSource)
}
