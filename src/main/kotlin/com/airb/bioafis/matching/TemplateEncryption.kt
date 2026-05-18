package com.airb.bioafis.matching

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class TemplateEncryption(
    @Value("\${bioafis.encryption.key}") private val keyBase64: String,
) {
    companion object {
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }

    private val key: SecretKey by lazy {
        val keyBytes = Base64.getDecoder().decode(keyBase64)
        require(keyBytes.size == 32) { "Encryption key must be exactly 32 bytes (256-bit)" }
        SecretKeySpec(keyBytes, "AES")
    }

    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > IV_LENGTH_BYTES) { "Ciphertext too short" }
        val iv = ciphertext.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = ciphertext.copyOfRange(IV_LENGTH_BYTES, ciphertext.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encrypted)
    }
}
