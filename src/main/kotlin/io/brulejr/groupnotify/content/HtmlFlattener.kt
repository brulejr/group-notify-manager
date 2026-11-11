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
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML as Markdown, for Discord and for switching a body from the WYSIWYG editor to the
 * Markdown one, or as plain text, for radio and other text-only channels.
 *
 * Covers what the editors produce: paragraphs, breaks, headings, emphasis, links, lists
 * (nested too), quotes, code, rules and simple tables. Anything else contributes its
 * text.
 */
object HtmlFlattener {

    fun toMarkdown(html: String): String = tidy(Converter(plain = false).block(Jsoup.parseBodyFragment(html).body()))

    fun toText(html: String): String = tidy(Converter(plain = true).block(Jsoup.parseBodyFragment(html).body()))

    private fun tidy(text: String): String = text
        .lines().joinToString("\n") { line -> if (line.endsWith("  ")) line.trimEnd() + "  " else line.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private class Converter(val plain: Boolean) {

        fun block(element: Element): String = element.childNodes().joinToString("") { node(it, inPre = false) }

        private fun inline(element: Element, inPre: Boolean = false): String =
            element.childNodes().joinToString("") { node(it, inPre) }

        private fun node(node: Node, inPre: Boolean): String = when (node) {
            is TextNode -> if (inPre) node.wholeText else escape(node.text())
            is Element -> element(node, inPre)
            else -> ""
        }

        private fun element(e: Element, inPre: Boolean): String {
            val tag = e.tagName().lowercase()
            return when (tag) {
                "p", "div", "section", "article", "header", "footer" -> paragraph(inline(e).trim())
                "br" -> if (plain) "\n" else "  \n"
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val text = inline(e).trim()
                    paragraph(if (plain) text else "#".repeat(tag[1].digitToInt()) + " " + text)
                }
                "strong", "b" -> wrap(inline(e), "**")
                "em", "i" -> wrap(inline(e), "*")
                "s", "del", "strike" -> wrap(inline(e), "~~")
                "code" -> if (inPre || plain) e.wholeText() else "`" + e.wholeText() + "`"
                "pre" -> paragraph(if (plain) e.wholeText().trimEnd() else "```\n" + e.wholeText().trimEnd() + "\n```")
                "a" -> link(e)
                "img" -> if (plain) e.attr("alt") else "![${e.attr("alt")}](${e.attr("src")})"
                "ul", "ol" -> paragraph(list(e, depth = 0))
                "blockquote" -> paragraph(block(e).trim().lines().joinToString("\n") { if (plain) "  $it" else "> $it" })
                "hr" -> paragraph(if (plain) "----" else "---")
                "table" -> paragraph(table(e))
                "script", "style", "head" -> ""
                else -> inline(e, inPre)
            }
        }

        private fun paragraph(text: String) = if (text.isBlank()) "" else "\n\n$text\n\n"

        // Emphasis around nothing, or around leading or trailing spaces, is not emphasis.
        private fun wrap(text: String, marker: String): String {
            if (plain || text.isBlank()) return text
            val lead = text.takeWhile { it == ' ' }
            val trail = text.takeLastWhile { it == ' ' }
            return lead + marker + text.trim() + marker + trail
        }

        private fun link(e: Element): String {
            val href = e.attr("href")
            val text = inline(e).trim()
            return when {
                href.isBlank() -> text
                plain -> if (text.isEmpty() || text == href || "mailto:$text" == href) href.removePrefix("mailto:") else "$text ($href)"
                text.isEmpty() || text == href -> "<$href>"
                else -> "[$text]($href)"
            }
        }

        private fun list(list: Element, depth: Int): String {
            val ordered = list.tagName().equals("ol", ignoreCase = true)
            val indent = "   ".repeat(depth)
            var number = list.attr("start").toIntOrNull() ?: 1
            return list.children().filter { it.tagName().equals("li", ignoreCase = true) }.joinToString("\n") { item ->
                // Quill 2 marks an ordered item in a bullet list, and the reverse, with data-list.
                val itemOrdered = when (item.attr("data-list")) { "ordered" -> true; "bullet" -> false; else -> ordered }
                val marker = if (itemOrdered) "${number++}." else "-"
                val nested = item.children().filter { it.tagName().lowercase() in setOf("ul", "ol") }
                nested.forEach { it.remove() }
                val text = block(item).trim().replace(Regex("\n{2,}"), "\n").replace("\n", "\n$indent   ")
                val sub = nested.joinToString("\n") { list(it, depth + 1) }
                "$indent$marker $text" + if (sub.isEmpty()) "" else "\n$sub"
            }
        }

        private fun table(table: Element): String {
            val rows = table.select("tr").map { row -> row.select("th, td").map { inline(it).trim().replace("|", "\\|") } }
            if (rows.isEmpty()) return ""
            if (plain) return rows.joinToString("\n") { it.joinToString(" | ") }
            val width = rows.maxOf { it.size }
            val padded = rows.map { it + List(width - it.size) { "" } }
            val header = "| " + padded.first().joinToString(" | ") + " |"
            val rule = "|" + List(width) { " --- " }.joinToString("|") + "|"
            return (listOf(header, rule) + padded.drop(1).map { "| " + it.joinToString(" | ") + " |" }).joinToString("\n")
        }

        // Only what would otherwise start emphasis or code; a full escape makes text unreadable.
        private fun escape(text: String) = if (plain) text else text.replace(Regex("([*_`])"), "\\\\$1")
    }
}
