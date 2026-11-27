package com.mntx.gitpulse.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM envelope encryption for the GitHub token, with the key material held by the
 * Android Keystore (hardware-backed on most devices). Tokens never touch plain SharedPreferences.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gitpulse_secure", Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun putToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit { remove(KEY_TOKEN) }
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val packed = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            prefs.edit { putString(KEY_TOKEN, packed) }
        }.onFailure {
            // Keystore can be unavailable on some emulators — never crash sign-in for that.
            prefs.edit(true) { putString(KEY_TOKEN, null) }
        }
    }

    fun getToken(): String? {
        val packed = prefs.getString(KEY_TOKEN, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            if (bytes.size <= IV_SIZE) return null
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val body = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clearToken() = prefs.edit { remove(KEY_TOKEN) }

    /** Generic encrypted string (used for the optional OAuth client secret). */
    fun putSecret(key: String, value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit { remove(key) }
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit { putString(key, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)) }
        }
    }

    fun getSecret(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            if (bytes.size <= IV_SIZE) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
            String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gitpulse_token_key"
        private const val KEY_TOKEN = "github_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        const val KEY_OAUTH_CLIENT_ID = "oauth_client_id"
        const val KEY_OAUTH_CLIENT_SECRET = "oauth_client_secret"
    }
}
