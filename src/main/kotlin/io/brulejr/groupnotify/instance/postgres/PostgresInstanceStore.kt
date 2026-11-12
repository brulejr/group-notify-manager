/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.postgres

import io.brulejr.groupnotify.instance.InstancePort
import io.brulejr.groupnotify.instance.PluginInstance
import io.brulejr.groupnotify.platform.getInstant
import io.brulejr.groupnotify.platform.toTimestamptz
import org.springframework.jdbc.core.simple.JdbcClient
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.util.UUID

/**
 * PostgreSQL-backed [InstancePort]: the `plugin_instances` table, configuration as
 * `jsonb` with its secrets sealed. A delivery refers to its instance by foreign key, so
 * the database itself refuses to delete one still in use.
 */
class PostgresInstanceStore(
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
) : InstancePort {

    override fun find(id: UUID): PluginInstance? =
        jdbc.sql("SELECT * FROM plugin_instances WHERE id = :id").param("id", id).query { rs, _ -> rs.toInstance() }.optional().orElse(null)

    override fun list(groups: Set<String>?): List<PluginInstance> = when {
        groups == null -> jdbc.sql("SELECT * FROM plugin_instances ORDER BY group_name, lower(name)")
        groups.isEmpty() -> return emptyList()
        else -> jdbc.sql("SELECT * FROM plugin_instances WHERE group_name IN (:groups) ORDER BY group_name, lower(name)")
            .param("groups", groups.toList())
    }.query { rs, _ -> rs.toInstance() }.list()

    override fun save(instance: PluginInstance) {
        jdbc.sql(
            """
            INSERT INTO plugin_instances (id, group_name, plugin, name, config, created_by, created_at, updated_by, updated_at)
            VALUES (:id, :group, :plugin, :name, CAST(:config AS jsonb), :createdBy, :createdAt, :updatedBy, :updatedAt)
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name, config = EXCLUDED.config, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at
            """,
        )
            .param("id", instance.id)
            .param("group", instance.group)
            .param("plugin", instance.plugin)
            .param("name", instance.name)
            .param("config", json.writeValueAsString(instance.config))
            .param("createdBy", instance.createdBy)
            .param("createdAt", instance.createdAt.toTimestamptz())
            .param("updatedBy", instance.updatedBy)
            .param("updatedAt", instance.updatedAt.toTimestamptz())
            .update()
    }

    override fun delete(id: UUID): Boolean =
        jdbc.sql("DELETE FROM plugin_instances WHERE id = :id").param("id", id).update() > 0

    private fun ResultSet.toInstance() = PluginInstance(
        id = getObject("id", UUID::class.java),
        group = getString("group_name"),
        plugin = getString("plugin"),
        name = getString("name"),
        config = json.readValue(getString("config"), CONFIG),
        createdBy = getString("created_by"),
        createdAt = checkNotNull(getInstant("created_at")),
        updatedBy = getString("updated_by"),
        updatedAt = checkNotNull(getInstant("updated_at")),
    )

    private companion object {
        val CONFIG = object : TypeReference<Map<String, String>>() {}
    }
}
