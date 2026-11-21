/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.user.postgres

import io.brulejr.groupnotify.platform.getInstant
import io.brulejr.groupnotify.platform.toTimestamptz
import io.brulejr.groupnotify.user.UserPort
import io.brulejr.groupnotify.user.UserRecord
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet

/** PostgreSQL-backed [UserPort]: the `users` table, groups as `jsonb`. */
class PostgresUserStore(private val jdbc: JdbcClient, private val json: JsonMapper) : UserPort {

    override fun find(username: String): UserRecord? =
        jdbc.sql("SELECT * FROM users WHERE username = :username").param("username", username).query { rs, _ -> rs.toRecord() }.optional().orElse(null)

    override fun list(): List<UserRecord> =
        jdbc.sql("SELECT * FROM users ORDER BY last_seen DESC").query { rs, _ -> rs.toRecord() }.list()

    override fun save(record: UserRecord) {
        jdbc.sql(
            """
            INSERT INTO users (username, display_name, email, groups, admin, first_seen, last_seen, visits, visit_started_at, last_address, last_agent)
            VALUES (:username, :displayName, :email, CAST(:groups AS jsonb), :admin, :firstSeen, :lastSeen, :visits, :visitStartedAt, :lastAddress, :lastAgent)
            ON CONFLICT (username) DO UPDATE
            SET display_name = EXCLUDED.display_name, email = EXCLUDED.email, groups = EXCLUDED.groups, admin = EXCLUDED.admin,
                last_seen = EXCLUDED.last_seen, visits = EXCLUDED.visits, visit_started_at = EXCLUDED.visit_started_at,
                last_address = EXCLUDED.last_address, last_agent = EXCLUDED.last_agent
            """,
        )
            .param("username", record.username)
            .param("displayName", record.displayName)
            .param("email", record.email)
            .param("groups", json.writeValueAsString(record.groups.sorted()))
            .param("admin", record.admin)
            .param("firstSeen", record.firstSeen.toTimestamptz())
            .param("lastSeen", record.lastSeen.toTimestamptz())
            .param("visits", record.visits)
            .param("visitStartedAt", record.visitStartedAt.toTimestamptz())
            .param("lastAddress", record.lastAddress)
            .param("lastAgent", record.lastAgent)
            .update()
    }

    override fun delete(username: String): Boolean =
        jdbc.sql("DELETE FROM users WHERE username = :username").param("username", username).update() > 0

    private fun ResultSet.toRecord() = UserRecord(
        username = getString("username"),
        displayName = getString("display_name"),
        email = getString("email"),
        groups = json.readValue(getString("groups"), GROUPS).toSortedSet(),
        admin = getBoolean("admin"),
        firstSeen = checkNotNull(getInstant("first_seen")),
        lastSeen = checkNotNull(getInstant("last_seen")),
        visits = getInt("visits"),
        visitStartedAt = checkNotNull(getInstant("visit_started_at")),
        lastAddress = getString("last_address"),
        lastAgent = getString("last_agent"),
    )

    private companion object {
        val GROUPS = object : TypeReference<List<String>>() {}
    }
}
