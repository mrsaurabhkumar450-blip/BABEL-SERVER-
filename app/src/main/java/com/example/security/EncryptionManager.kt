package com.example.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_security_vault", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_PIN_HASH = "security_pin_hash"
        private const val PREF_PIN_SALT = "security_pin_salt"
        private const val PREF_SECURITY_ENABLED = "security_lock_enabled"
        private const val PREF_LANGUAGE = "jarvis_language_pref" // "hinglish", "hindi", "english"
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    var isUnlocked: Boolean = !isSecurityEnabled()
        private set

    fun isSecurityEnabled(): Boolean {
        return prefs.getBoolean(PREF_SECURITY_ENABLED, false)
    }

    fun setSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SECURITY_ENABLED, enabled).apply()
        if (!enabled) {
            isUnlocked = true
        }
    }

    fun hasPin(): Boolean {
        return !prefs.getString(PREF_PIN_HASH, null).isNullOrEmpty()
    }

    fun setPin(pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val hash = hashPinWithSalt(pin, salt)
        prefs.edit()
            .putString(PREF_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(PREF_PIN_HASH, hash)
            .putBoolean(PREF_SECURITY_ENABLED, true)
            .apply()
        isUnlocked = true
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(PREF_PIN_HASH, null) ?: return false
        val storedSaltB64 = prefs.getString(PREF_PIN_SALT, null) ?: return false
        val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val candidateHash = hashPinWithSalt(pin, salt)
        val match = storedHash == candidateHash
        if (match) {
            isUnlocked = true
        }
        return match
    }

    fun lock() {
        if (isSecurityEnabled()) {
            isUnlocked = false
        }
    }

    fun unlock() {
        isUnlocked = true
    }

    fun getPreferredLanguage(): String {
        return prefs.getString(PREF_LANGUAGE, "hinglish") ?: "hinglish"
    }

    fun setPreferredLanguage(lang: String) {
        prefs.edit().putString(PREF_LANGUAGE, lang).apply()
    }

    private fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    // AES-GCM Encrypt
    fun encrypt(plainText: String, masterPass: String = "JARVIS_CORE_AES_SEC"): String {
        try {
            val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }

            val keySpec = PBEKeySpec(masterPass.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec)
            val key = SecretKeySpec(secretKey.encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Pack salt + iv + cipherText
            val combined = ByteArray(salt.size + iv.size + cipherText.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            return plainText
        }
    }

    // AES-GCM Decrypt
    fun decrypt(encryptedText: String, masterPass: String = "JARVIS_CORE_AES_SEC"): String {
        try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size < 16 + GCM_IV_LENGTH) return encryptedText

            val salt = ByteArray(16)
            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - 16 - GCM_IV_LENGTH)

            System.arraycopy(combined, 0, salt, 0, 16)
            System.arraycopy(combined, 16, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, 16 + GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val keySpec = PBEKeySpec(masterPass.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec)
            val key = SecretKeySpec(secretKey.encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val plain = cipher.doFinal(cipherText)

            return String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            return encryptedText
        }
    }
}
