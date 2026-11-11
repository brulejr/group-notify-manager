/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.content

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlFlattenerTest {

    @Test
    fun `what the WYSIWYG editor writes becomes Markdown`() {
        val html = """
            <h2>Meeting</h2><p>Bring <strong>your radio</strong> and <em>a friend</em>.</p>
            <ul><li>Agenda<ul><li>Elections</li></ul></li><li><a href="https://club.example.org">Website</a></li></ul>
            <blockquote>Seventy-three</blockquote><p>Call <a href="mailto:sec@example.org">sec@example.org</a><br>today</p>
        """.trimIndent()

        assertEquals(
            """
            ## Meeting

            Bring **your radio** and *a friend*.

            - Agenda
               - Elections
            - [Website](https://club.example.org)

            > Seventy-three

            Call [sec@example.org](mailto:sec@example.org)  
            today
            """.trimIndent(),
            HtmlFlattener.toMarkdown(html),
        )
    }

    @Test
    fun `plain text keeps the words and the links, and numbers the lists`() {
        val html = """<p>See <a href="https://x.org">the site</a></p><ol><li>first</li><li>second</li></ol><p>Done</p>"""

        assertEquals("See the site (https://x.org)\n\n1. first\n2. second\n\nDone", HtmlFlattener.toText(html))
    }

    @Test
    fun `Quill's own list markup is understood`() {
        val html = """<ol><li data-list="bullet">a</li><li data-list="bullet">b</li></ol>"""

        assertEquals("- a\n- b", HtmlFlattener.toMarkdown(html))
    }

    @Test
    fun `characters that would start emphasis are escaped`() {
        assertEquals("""snake\_case and 2\*3""", HtmlFlattener.toMarkdown("<p>snake_case and 2*3</p>"))
    }
}
