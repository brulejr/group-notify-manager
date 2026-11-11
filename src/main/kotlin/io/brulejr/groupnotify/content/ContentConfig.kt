/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Rendering notification bodies: Markdown, sanitised HTML, plain text and placeholders. */
@Configuration
class ContentConfig {

    @Bean
    fun contentService(datafill: ContentDatafill) = ContentService(datafill)

    @Bean
    fun contentHealthIndicator(service: ContentService) = ContentHealthIndicator(service)

    @Bean
    fun contentInfoContributor(datafill: ContentDatafill) = ContentInfoContributor(datafill)
}
