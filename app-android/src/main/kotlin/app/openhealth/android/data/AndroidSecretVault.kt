package app.openhealth.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.openhealth.core.seam.SecretVault
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets behind the Android Keystore (`Tech-Stack.md` §5, §8). Each secret is encrypted
 * with a non-exportable AES-256-GCM key held by the Keystore and stored as IV plus
 * ciphertext in app-private preferences. Nothing leaves the device and no key material
 * is ever written in clear.
 */
class AndroidSecretVault(context: Context, preferencesName: String = "openhealth.vault") :
    SecretVault {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    override fun has(name: String): Boolean = preferences.contains(name)

    @Synchronized
    override fun get(name: String): ByteArray? {
        val stored = preferences.getString(name, null)
        val separator = stored?.indexOf(SEPARATOR) ?: -1
        return if (stored == null || separator <= 0) {
            null
        } else {
            val iv = Base64.decode(stored.substring(0, separator), Base64.NO_WRAP)
            val ciphertext = Base64.decode(stored.substring(separator + 1), Base64.NO_WRAP)
            decrypt(name, iv, ciphertext)
        }
    }

    private fun decrypt(name: String, iv: ByteArray, ciphertext: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    } catch (failure: java.security.GeneralSecurityException) {
        // An invalidated Keystore key cannot decrypt. The name, never the value, is
        // logged for a local diagnosis; getOrCreate refuses to replace a lost key.
        android.util.Log.w(TAG, "Secret $name could not be decrypted", failure)
        null
    }

    @Synchronized
    override fun put(name: String, value: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value)
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        preferences.edit().putString(name, encoded).apply()
    }

    @Synchronized
    override fun remove(name: String) {
        preferences.edit().remove(name).apply()
    }

    /** The database passphrase, created once and never re-created silently after loss. */
    @Synchronized
    fun getOrCreate(name: String, size: Int = PASSPHRASE_BYTES): ByteArray {
        get(name)?.let { return it }
        if (has(name)) {
            error("Secret $name exists but cannot be decrypted: its Keystore key is gone")
        }
        val secret = ByteArray(size).also { java.security.SecureRandom().nextBytes(it) }
        put(name, secret)
        return secret
    }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "openhealth.vault.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val PASSPHRASE_BYTES = 32
        const val SEPARATOR = ':'
        const val TAG = "openhealth.vault"
    }
}
