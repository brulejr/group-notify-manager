/*
 * Copyright (c) 2026 Jon Brule
 *
 * Licensed under the MIT License; see LICENSE in the project root for the full
 * license text.
 *
 * SPDX-License-Identifier: MIT
 */
package io.brulejr.groupnotify.instance

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals instance secrets (API keys, webhook URLs) for storage with AES-256-GCM, so the
 * database or a backup alone never reveals them. A sealed value is
 * `enc:v1:<base64 of a fresh 12-byte nonce, then the ciphertext and its tag>`; the tag
 * also detects a value altered in the database.
 *
 * The key comes from `notify.instances.secret-key` (`NOTIFY_SECRET_KEY`), 32 bytes in
 * base64, e.g. from `openssl rand -base64 32`. Without one, a random key is made for this
 * run only: fine in memory, but secrets stored in PostgreSQL then cannot be opened after
 * a restart, and [ephemeral] says so in health.
 */
class SecretCipher(base64Key: String?) {

    /** No key was configured; this run's secrets die with it. */
    val ephemeral: Boolean = base64Key.isNullOrBlank()

    private val key = SecretKeySpec(
        if (ephemeral) ByteArray(KEY_BYTES).also(random::nextBytes) else decodeKey(checkNotNull(base64Key)),
        "AES",
    )

    /** [plain] sealed; a fresh nonce each time, so equal secrets do not look equal. */
    fun seal(plain: String): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce)) }
        return PREFIX + Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /**
     * A sealed value opened, or [plain] itself if it was never sealed. Throws
     * [SecretUnreadableException] when it was sealed with another key, or altered.
     */
    fun open(value: String): String {
        if (!isSealed(value)) return value
        return try {
            val bytes = Base64.getDecoder().decode(value.removePrefix(PREFIX))
            require(bytes.size > NONCE_BYTES) { "too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
            }
            String(cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw SecretUnreadableException(e)
        } catch (e: IllegalArgumentException) {
            throw SecretUnreadableException(e)
        }
    }

    fun isSealed(value: String) = value.startsWith(PREFIX)

    companion object {
        const val PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private val random = SecureRandom()

        /** Why [key] is not usable, or null if it is. */
        fun problemWith(key: String): String? {
            val bytes = runCatching { Base64.getDecoder().decode(key.trim()) }.getOrNull()
                ?: return "is not base64"
            return if (bytes.size != KEY_BYTES) "must be $KEY_BYTES bytes, not ${bytes.size} (openssl rand -base64 32)" else null
        }

        private fun decodeKey(key: String): ByteArray {
            problemWith(key)?.let { throw IllegalArgumentException("notify.instances.secret-key $it") }
            return Base64.getDecoder().decode(key.trim())
        }
    }
}
