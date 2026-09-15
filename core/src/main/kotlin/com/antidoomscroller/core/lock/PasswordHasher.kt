package com.antidoomscroller.core.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 hashing for the strict-mode password.
 *
 * The password never leaves the device and is never stored in the clear; only this digest is
 * written. Comparison is constant time so a wrong guess leaks nothing through timing.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 150_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val PREFIX = "pbkdf2_sha256"

    fun hash(password: CharArray, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val digest = derive(password, salt, ITERATIONS)
        val encoder = Base64.getEncoder().withoutPadding()
        return listOf(
            PREFIX,
            ITERATIONS.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(digest),
        ).joinToString("$")
    }

    fun verify(password: CharArray, encoded: String?): Boolean {
        if (encoded.isNullOrBlank()) return false
        val parts = encoded.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        return try {
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            MessageDigest.isEqual(derive(password, salt, iterations), expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
