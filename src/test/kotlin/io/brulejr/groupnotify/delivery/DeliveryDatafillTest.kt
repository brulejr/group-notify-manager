/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryDatafillTest {

    private fun bind(source: org.springframework.core.env.PropertySource<*>): DeliveryDatafill {
        // Spring maps variable names to property names only for the source it knows as the
        // environment's own, so a stand-in takes its name.
        val environment = StandardEnvironment().apply {
            if (source is SystemEnvironmentPropertySource) {
                propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source)
            } else {
                propertySources.addFirst(source)
            }
        }
        return Binder(ConfigurationPropertySources.get(environment)).bindOrCreate("notify.delivery", DeliveryDatafill::class.java)
    }

    @Test
    fun `plugin settings bind from YAML in kebab case`() {
        val datafill = bind(MapPropertySource("yaml", mapOf("notify.delivery.settings.sms.account-sid" to "AC1")))

        assertEquals(mapOf("account-sid" to "AC1"), datafill.settingsFor("sms"))
    }

    @Test
    fun `plugin settings bind from environment variables to the same kebab-case keys`() {
        val datafill = bind(
            SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                mapOf("NOTIFY_DELIVERY_SETTINGS_SMS_AUTH_TOKEN" to "t0k3n", "NOTIFY_DELIVERY_SETTINGS_SMS_ACCOUNT_SID" to "AC9"),
            ),
        )

        assertEquals(mapOf("auth-token" to "t0k3n", "account-sid" to "AC9"), datafill.settingsFor("sms"))
    }

    @Test
    fun `keys written in camel case are found too`() {
        assertEquals("account-sid", DeliveryDatafill.normalizeKey("accountSid"))
        assertEquals("account-sid", DeliveryDatafill.normalizeKey("ACCOUNT_SID"))
    }
}
