/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * CommonMark with GitHub's tables, strikethrough and bare-URL links. A single line break
 * is kept as a break: notifications are written line by line, and an author who presses
 * Enter expects a new line, not a joined paragraph. The output is not yet safe; it goes
 * through [HtmlSanitizer] like any other HTML.
 */
class MarkdownRenderer {

    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).softbreak("<br>\n").build()

    fun toHtml(markdown: String): String = renderer.render(parser.parse(markdown))
}
