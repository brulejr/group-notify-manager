/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import io.brulejr.groupnotify.notification.BodyFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentServiceTest {

    private val content = ContentService(ContentDatafill())

    @Test
    fun `Markdown renders with tables, strikethrough, links and line breaks`() {
        val html = content.html("Line one\nline two\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n~~gone~~ https://example.org", BodyFormat.MARKDOWN)

        assertTrue("Line one<br>" in html, html)
        assertTrue("<table>" in html)
        assertTrue("<del>gone</del>" in html)
        assertTrue("""<a href="https://example.org" rel="noopener noreferrer">""" in html, html)
    }

    @Test
    fun `scripts, handlers and javascript links never survive`() {
        val dirty = """<p onclick="steal()">Hi<script>alert(1)</script> <a href="javascript:alert(2)">x</a> <img src="x" onerror="boom()"></p>"""

        val html = content.html(dirty, BodyFormat.HTML)
        assertFalse("script" in html || "onclick" in html || "javascript" in html || "onerror" in html, html)
        val fromMarkdown = content.html("<script>alert(1)</script>\n\n[x](javascript:alert(1))", BodyFormat.MARKDOWN)
        assertFalse("script" in fromMarkdown || "javascript" in fromMarkdown, fromMarkdown)
    }

    @Test
    fun `placeholders are filled, escaped in HTML, and unknown ones left as written`() {
        assertEquals("Hi Club, {{nope}}", Placeholders.fill("Hi {{ group }}, {{nope}}", mapOf("group" to "Club")))
        assertEquals("&lt;b&gt;", Placeholders.fill("{{title}}", mapOf("title" to "<b>"), html = true))
    }

    @Test
    fun `switching editors converts the body both ways`() {
        val html = content.convert("**bold** and *em*\n\n- one\n- two", BodyFormat.MARKDOWN, BodyFormat.HTML)
        assertTrue("<strong>bold</strong>" in html && "<ul>" in html, html)

        assertEquals("**bold** and *em*\n\n- one\n- two", content.convert(html, BodyFormat.HTML, BodyFormat.MARKDOWN))
    }
}
