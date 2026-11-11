/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.platform

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The PostgreSQL schema every table of this application lives in. The connection itself
 * is Spring Boot's `spring.datasource`.
 *
 * A schema of its own rather than `public`, so the database can be shared with other
 * applications without this one's tables mixing with theirs. Connections are pointed at
 * it through `spring.datasource.hikari.schema`, which application.yml takes from this
 * same property, so the SQL in the stores names no schema.
 *
 * @property schema created by the first migration if it does not already exist.
 */
@ConfigurationProperties("notify.database")
data class DatabaseDatafill(
    val schema: String = "group_notify",
) {
    init {
        // Lower-case and unquoted, so it needs no quoting anywhere it is written.
        require(Regex("[a-z_][a-z0-9_]*").matches(schema)) {
            "notify.database.schema must be a lower-case SQL identifier, not '$schema'"
        }
    }
}
