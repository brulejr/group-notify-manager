/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

/**
 * Keeps the formatting an editor produces and nothing that runs: no scripts, no event
 * attributes, no `javascript:` links, no styles. Everything shown in the console or sent
 * as HTML has been through here, whoever wrote it.
 */
object HtmlSanitizer {

    private val safelist = Safelist.relaxed()
        .addTags("s", "del", "strike", "u", "hr", "span")
        // Quill marks alignment and indentation with classes; harmless, and kept so a
        // body edited again looks as it did.
        .addAttributes("p", "class")
        .addAttributes("li", "class", "data-list")
        .addAttributes("span", "class")
        .addAttributes("a", "rel")
        .addProtocols("a", "href", "tel")
        .addEnforcedAttribute("a", "rel", "noopener noreferrer")

    private val output = Document.OutputSettings().prettyPrint(false)

    fun clean(html: String): String = Jsoup.clean(html, "", safelist, output)
}
