/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content.web

import io.brulejr.groupnotify.content.ContentService
import io.brulejr.groupnotify.notification.BodyFormat
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/** The editor's helpers: a preview of the body as sent, and rewriting it for the other editor. */
@Controller
@RequestMapping("/ui/content")
class ContentPageController(private val content: ContentService) {

    /** The body as recipients get it: sanitised HTML, the Markdown Discord gets, and plain text. */
    @PostMapping("/preview")
    fun preview(@RequestParam(defaultValue = "") body: String, @RequestParam(defaultValue = "MARKDOWN") format: BodyFormat, model: Model): String {
        model.addAttribute("html", content.html(body, format))
        model.addAttribute("markdown", content.markdown(body, format))
        model.addAttribute("text", content.text(body, format))
        return "ui/notifications/parts :: preview"
    }

    /** The body rewritten from one editor's format to the other's, as plain text for the editor to load. */
    @PostMapping("/convert", produces = [MediaType.TEXT_PLAIN_VALUE])
    @ResponseBody
    fun convert(@RequestParam(defaultValue = "") body: String, @RequestParam from: BodyFormat, @RequestParam to: BodyFormat): String =
        content.convert(body, from, to)
}
