/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretCipherTest {

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val cipher = SecretCipher(key)

    @Test
    fun `a sealed secret opens to itself, and never looks like it`() {
        val sealed = cipher.seal("https://discord.com/api/webhooks/42/s3cret")

        assertTrue(sealed.startsWith("enc:v1:"))
        assertFalse("s3cret" in sealed)
        assertEquals("https://discord.com/api/webhooks/42/s3cret", cipher.open(sealed))
        assertNotEquals(sealed, cipher.seal("https://discord.com/api/webhooks/42/s3cret"), "a fresh nonce each time")
    }

    @Test
    fun `another key, or an altered value, cannot be opened`() {
        val sealed = cipher.seal("s3cret")
        val other = SecretCipher(Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))

        assertFailsWith<SecretUnreadableException> { other.open(sealed) }
        val bytes = Base64.getDecoder().decode(sealed.removePrefix(SecretCipher.PREFIX)).also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertFailsWith<SecretUnreadableException> { cipher.open(SecretCipher.PREFIX + Base64.getEncoder().encodeToString(bytes)) }
        assertFailsWith<SecretUnreadableException> { cipher.open(SecretCipher.PREFIX + "not base64!") }
    }

    @Test
    fun `without a key one is made for the run, and a bad key is refused`() {
        val ephemeral = SecretCipher(null)

        assertTrue(ephemeral.ephemeral)
        assertEquals("x", ephemeral.open(ephemeral.seal("x")))
        assertFalse(cipher.ephemeral)
        assertNull(SecretCipher.problemWith(key))
        assertNotNull(SecretCipher.problemWith("c2hvcnQ="))
        assertFailsWith<IllegalArgumentException> { InstanceDatafill(secretKey = "c2hvcnQ=") }
    }
}
