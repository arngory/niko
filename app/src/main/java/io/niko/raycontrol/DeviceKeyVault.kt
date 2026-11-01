package io.niko.raycontrol

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps raycontrol device keys with a non-exportable AES-256-GCM key in the Android Keystore. Only the wrapped
 * form is stored, so a copy of the app data (backup, root dump of the database) does not carry the device key.
 */
object DeviceKeyVault {
    private const val ALIAS = "niko-raycontrol-device"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    /** base64(iv ‖ ciphertext) */
    fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return (cipher.iv + cipher.doFinal(plain)).toByteString().base64()
    }

    fun unwrap(wrapped: String): ByteArray {
        val raw = wrapped.decodeBase64()?.toByteArray() ?: error("device key: not base64")
        require(raw.size > IV_SIZE) { "device key: too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_SIZE))
        }
        return cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE)
    }
}
