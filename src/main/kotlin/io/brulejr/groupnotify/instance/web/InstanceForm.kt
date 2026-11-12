/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance.web

import io.brulejr.groupnotify.instance.InstanceDraft
import io.brulejr.groupnotify.instance.PluginInstance
import org.springframework.util.MultiValueMap

/**
 * An instance's form as submitted, kept as typed so a refused form is redrawn as it was.
 * A field's input is named `config.<key>`.
 */
data class InstanceForm(
    val group: String = "",
    val plugin: String = "",
    val name: String = "",
    val config: Map<String, String> = emptyMap(),
) {
    fun toDraft() = InstanceDraft(group, plugin, name, config)

    companion object {
        fun from(params: MultiValueMap<String, String>) = InstanceForm(
            group = params.getFirst("group").orEmpty(),
            plugin = params.getFirst("plugin").orEmpty(),
            name = params.getFirst("name").orEmpty(),
            // A checkbox is sent with a hidden "false" before it, so the last value wins.
            config = params.filterKeys { it.startsWith("config.") }
                .mapKeys { it.key.removePrefix("config.") }
                .mapValues { it.value.lastOrNull().orEmpty() },
        )

        /** A stored instance as its form shows it: with [masked] configuration, never a secret. */
        fun of(instance: PluginInstance, masked: Map<String, String>) =
            InstanceForm(instance.group, instance.plugin, instance.name, masked)
    }
}
