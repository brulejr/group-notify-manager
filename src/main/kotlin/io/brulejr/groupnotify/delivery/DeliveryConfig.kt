/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.DeliveryPlugin
import io.brulejr.groupnotify.plugin.PluginSettings
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/**
 * The delivery plugins: finding them, lending them HTTP, JSON, mail and their settings,
 * and stopping them at shutdown.
 */
@Configuration
class DeliveryConfig {

    @Bean
    fun pluginHttpClient(datafill: DeliveryDatafill): HttpClient = HttpClient.newBuilder()
        .connectTimeout(datafill.connectTimeout)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Present only when `spring.mail.host` names an SMTP server. application.yml sets it
     * from `MAIL_HOST`, which is blank when unset, and Spring Boot builds a sender for a
     * blank host too; one that cannot connect anywhere is no mailer.
     */
    @Bean
    fun pluginMailer(datafill: DeliveryDatafill, sender: ObjectProvider<JavaMailSender>): PluginMailer =
        PluginMailer(
            sender.ifAvailable
                ?.takeUnless { it is JavaMailSenderImpl && it.host.isNullOrBlank() }
                ?.let { SpringMailer(it, datafill.mail) },
        )

    @Bean
    fun pluginHandles(
        datafill: DeliveryDatafill,
        http: HttpClient,
        mailer: PluginMailer,
        jsonMapper: JsonMapper,
    ): List<PluginHandle> {
        val builtIn = listOfNotNull(LogPlugin().takeIf { datafill.logPlugin })
        val loader = PluginLoader(datafill) { plugin ->
            HostPluginContext(PluginSettings(datafill.settingsFor(plugin.id)), http, mailer.mailer, Slf4jPluginLog(plugin.id), jsonMapper)
        }
        // The context class loader, which under PropertiesLauncher also sees loader.path's jars.
        val classLoader = Thread.currentThread().contextClassLoader ?: DeliveryPlugin::class.java.classLoader
        return loader.load(builtIn, classLoader)
    }

    /** Stops every plugin when the application stops. */
    @Bean(destroyMethod = "stop")
    fun deliveryService(handles: List<PluginHandle>, datafill: DeliveryDatafill) = DeliveryService(handles, datafill)

    @Bean
    fun deliveryHealthIndicator(service: DeliveryService) = DeliveryHealthIndicator(service)

    @Bean
    fun deliveryInfoContributor(service: DeliveryService, datafill: DeliveryDatafill) = DeliveryInfoContributor(service, datafill)
}
