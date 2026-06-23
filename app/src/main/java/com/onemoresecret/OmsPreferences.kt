package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object OmsPreferences {
    private const val PREFS_NAME = "MainActivity_secure"

    @Volatile
    private var instance: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createOmsEncryptedSharedPreferences(context).also { instance = it }
        }
    }

    private fun createOmsEncryptedSharedPreferences(context: Context): SharedPreferences {
        val delegate = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return OmsEncryptedSharedPreferences(delegate)
    }
}

class OmsEncryptedSharedPreferences(
    private val delegate: SharedPreferences
) : SharedPreferences {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "OmsPreferencesKey"
        private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private fun hashKey(key: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
            return Base64.encodeToString(hashBytes, Base64.NO_WRAP or Base64.URL_SAFE)
        }
    }

    private val secretKey: SecretKey by lazy {
        getOrCreateSecretKey()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
        if (combined.isEmpty()) throw IllegalArgumentException("Empty data")
        val ivSize = combined[0].toInt()
        if (ivSize <= 0 || ivSize > combined.size - 1) {
            throw IllegalArgumentException("Invalid IV size")
        }
        val iv = ByteArray(ivSize)
        val ciphertext = ByteArray(combined.size - 1 - ivSize)
        System.arraycopy(combined, 1, iv, 0, ivSize)
        System.arraycopy(combined, 1 + ivSize, ciphertext, 0, ciphertext.size)

        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    private fun encryptValue(value: Any?): String? {
        if (value == null) return null
        val serialized = when (value) {
            is String -> "S:$value"
            is Int -> "I:$value"
            is Long -> "L:$value"
            is Float -> "F:$value"
            is Boolean -> "B:$value"
            is Set<*> -> {
                val joined = value.filterIsInstance<String>().joinToString("\u0000")
                "SET:$joined"
            }
            else -> throw IllegalArgumentException("Unsupported type: ${value.javaClass}")
        }
        return encrypt(serialized)
    }

    private fun decryptValue(encryptedBase64: String?): Any? {
        if (encryptedBase64 == null) return null
        val decrypted = decrypt(encryptedBase64)
        val parts = decrypted.split(":", limit = 2)
        if (parts.size != 2) throw IllegalStateException("Invalid data format")
        val type = parts[0]
        val valueStr = parts[1]
        return when (type) {
            "S" -> valueStr
            "I" -> valueStr.toInt()
            "L" -> valueStr.toLong()
            "F" -> valueStr.toFloat()
            "B" -> valueStr.toBoolean()
            "SET" -> {
                if (valueStr.isEmpty()) emptySet<String>()
                else valueStr.split("\u0000").toSet()
            }
            else -> throw IllegalStateException("Unsupported serialized type: $type")
        }
    }

    override fun getAll(): Map<String, *> {
        return emptyMap<String, Any>()
    }

    override fun getString(key: String, defValue: String?): String? {
        val hashed = hashKey(key)
        val raw = delegate.getString(hashed, null) ?: return defValue
        return try {
            decryptValue(raw) as? String ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val hashed = hashKey(key)
        val raw = delegate.getString(hashed, null) ?: return defValues
        return try {
            decryptValue(raw) as? Set<String> ?: defValues
        } catch (_: Exception) {
            defValues
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        val hashed = hashKey(key)
        val raw = delegate.getString(hashed, null) ?: return defValue
        return try {
            decryptValue(raw) as? Int ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun getLong(key: String, defValue: Long): Long {
        val hashed = hashKey(key)
        val raw = delegate.getString(hashed, null) ?: return defValue
        return try {
            decryptValue(raw) as? Long ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val hashed = hashKey(key)
        val raw = delegate.getString(hashed, null) ?: return defValue
        return try {
            decryptValue(raw) as? Float ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val hashed = hashKey(key)
        val raw = delegate.getString(hashed, null) ?: return defValue
        return try {
            decryptValue(raw) as? Boolean ?: defValue
        } catch (_: Exception) {
            defValue
        }
    }

    override fun contains(key: String): Boolean {
        return delegate.contains(hashKey(key))
    }

    override fun edit(): SharedPreferences.Editor {
        return OmsEditor(delegate.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        delegate.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }

    inner class OmsEditor(private val editorDelegate: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            val hashed = hashKey(key)
            if (value == null) {
                editorDelegate.remove(hashed)
            } else {
                editorDelegate.putString(hashed, encryptValue(value))
            }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            val hashed = hashKey(key)
            if (values == null) {
                editorDelegate.remove(hashed)
            } else {
                editorDelegate.putString(hashed, encryptValue(values))
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            editorDelegate.putString(hashKey(key), encryptValue(value))
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            editorDelegate.putString(hashKey(key), encryptValue(value))
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            editorDelegate.putString(hashKey(key), encryptValue(value))
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            editorDelegate.putString(hashKey(key), encryptValue(value))
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            editorDelegate.remove(hashKey(key))
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            editorDelegate.clear()
            return this
        }

        override fun commit(): Boolean {
            return editorDelegate.commit()
        }

        override fun apply() {
            editorDelegate.apply()
        }
    }
}
