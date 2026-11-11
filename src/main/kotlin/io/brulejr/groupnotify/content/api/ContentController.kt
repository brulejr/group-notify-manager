/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content.api

import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.notification.BodyFormat
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Renders a body without saving it: what each plugin would be handed. */
@RestController
@RequestMapping("/api/content")
class ContentController(private val content: ContentService) {

    data class PreviewRequest(val body: String, val format: BodyFormat = BodyFormat.MARKDOWN)

    data class Preview(val html: String, val markdown: String, val text: String)

    @PostMapping("/preview")
    fun preview(@RequestBody request: PreviewRequest) = Preview(
        html = content.html(request.body, request.format),
        markdown = content.markdown(request.body, request.format),
        text = content.text(request.body, request.format),
    )
}
