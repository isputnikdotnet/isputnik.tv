package net.isputnik.tv.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Somewhere to keep one string between runs. An interface so tests need no device. */
interface TextStore {
    fun read(): String?
    fun write(text: String?)
}

/** What's kept is a long-lived session cookie, so it's encrypted with a key that never
 *  leaves the Android Keystore. A file that can't be read is treated as no file: the TV
 *  is simply linked again. */
class KeystoreTextStore(context: Context) : TextStore {
    private val file = File(context.filesDir, "session.bin")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    override fun read(): String? = try {
        val (iv, data) = file.readText().split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data))
    } catch (_: Exception) {
        null
    }

    override fun write(text: String?) {
        if (text == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encode = { bytes: ByteArray -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
        val temp = File(file.path + ".tmp")
        temp.writeText(encode(cipher.iv) + ":" + encode(cipher.doFinal(text.toByteArray())))
        temp.renameTo(file)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "isputnik_tv_session"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
