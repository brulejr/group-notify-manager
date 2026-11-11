/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.delivery

import io.brulejr.groupnotify.plugin.MailMessage
import io.brulejr.groupnotify.plugin.Mailer
import jakarta.mail.internet.InternetAddress
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

/** The application's SMTP connection, as plugins see it. */
class SpringMailer(
    private val sender: JavaMailSender,
    private val mail: DeliveryDatafill.Mail,
) : Mailer {

    override val defaultFrom: String get() = mail.from

    override fun send(message: MailMessage) {
        val mime = sender.createMimeMessage()
        MimeMessageHelper(mime, message.html != null, "UTF-8").apply {
            val from = message.from ?: mail.from
            if (message.from == null && mail.fromName != null) setFrom(InternetAddress(from, mail.fromName, "UTF-8")) else setFrom(from)
            if (message.to.isNotEmpty()) setTo(message.to.toTypedArray())
            if (message.cc.isNotEmpty()) setCc(message.cc.toTypedArray())
            if (message.bcc.isNotEmpty()) setBcc(message.bcc.toTypedArray())
            message.replyTo?.let { setReplyTo(it) }
            setSubject(message.subject)
            message.html?.let { setText(message.text, it) } ?: setText(message.text)
        }
        sender.send(mime)
    }
}
