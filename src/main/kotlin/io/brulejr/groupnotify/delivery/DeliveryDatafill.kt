/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * The delivery plugins and how deliveries are made.
 *
 * @property settings each plugin's installation settings, by plugin id, e.g.
 *   `notify.delivery.settings.sms.account-sid`. Keys are matched in kebab case whichever
 *   way they are written, so `NOTIFY_DELIVERY_SETTINGS_SMS_ACCOUNT_SID` works too.
 * @property disabled plugin ids not to load, even though their jar is present.
 * @property logPlugin offers the built-in `log` plugin, which only writes to the log:
 *   for trying a schedule out.
 * @property connectTimeout for the HTTP client plugins share.
 * @property maxAttempts tries per delivery, the first included, when a plugin says a
 *   failure is worth retrying.
 * @property retryBackoff the wait before the second try; it doubles each time after.
 * @property mail the sender of email, used by the email and groups.io plugins. The SMTP
 *   server itself is Spring Boot's `spring.mail.*`; with no `spring.mail.host` they
 *   load but are not ready.
 */
@ConfigurationProperties("notify.delivery")
data class DeliveryDatafill(
    val settings: Map<String, Map<String, String>> = emptyMap(),
    val disabled: Set<String> = emptySet(),
    val logPlugin: Boolean = true,
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val maxAttempts: Int = 3,
    val retryBackoff: Duration = Duration.ofSeconds(5),
    val mail: Mail = Mail(),
) {
    init {
        require(maxAttempts in 1..10) { "notify.delivery.max-attempts must be between 1 and 10" }
        require(!retryBackoff.isNegative) { "notify.delivery.retry-backoff must not be negative" }
    }

    /**
     * @property from the address mail is sent from. groups.io only accepts posts from a
     *   member, so for groups.io it must be subscribed to the group.
     * @property fromName shown beside it, e.g. `Radio Club`.
     */
    data class Mail(
        val from: String = "notify@localhost",
        val fromName: String? = null,
    )

    /** A plugin's settings with every key in kebab case: `account.sid`, `account_sid` and `accountSid` alike. */
    fun settingsFor(pluginId: String): Map<String, String> = settings.entries
        .filter { normalizeKey(it.key) == pluginId }
        .flatMap { it.value.entries }
        .associate { normalizeKey(it.key) to it.value }

    companion object {
        fun normalizeKey(key: String): String =
            key.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase().replace('.', '-').replace('_', '-')
    }
}
