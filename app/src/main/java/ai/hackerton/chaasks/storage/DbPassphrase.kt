package ai.hackerton.chaasks.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * DB 암호 문구 관리. 무작위 32바이트를 Android Keystore의 AES 키로 암호화해 SharedPreferences에 둔다.
 * 동의 철회 시 [delete]로 키와 보관값을 모두 지운다.
 */
object DbPassphrase {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "chaasks_db_key"
    private const val PREFS = "db_passphrase"
    private const val PREF_CIPHERTEXT = "ciphertext"
    private const val PREF_IV = "iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PASSPHRASE_BYTES = 32
    private const val GCM_TAG_BITS = 128

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(PREF_CIPHERTEXT, null)
        val iv = prefs.getString(PREF_IV, null)
        if (ciphertext != null && iv != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, decode(iv)))
            return cipher.doFinal(decode(ciphertext))
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        prefs.edit()
            .putString(PREF_CIPHERTEXT, encode(cipher.doFinal(passphrase)))
            .putString(PREF_IV, encode(cipher.iv))
            .commit()
        return passphrase
    }

    fun delete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
