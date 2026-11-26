package am.noz.caelum.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.SessionTokens
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenVault {
    fun getTokens(did: String): SessionTokens?
    fun saveTokens(did: String, tokens: SessionTokens)
    fun removeTokens(did: String)
    fun clearAll()
}

@Serializable
private data class EncryptedTokensPayload(
    val accessJwt: String,
    val refreshJwt: String,
)

/**
 * Hardware/AndroidKeyStore AES-256-GCM backed token vault.
 * Stores encrypted refresh/access JWT pairs per DID without exposing secrets in DataStore or logs.
 */
class AndroidKeystoreTokenVault(
    context: Context,
    prefsName: String = "caelum_token_vault",
    private val keyAlias: String = "caelum_session_aes_key_v1",
) : TokenVault {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun getTokens(did: String): SessionTokens? = synchronized(lock) {
        val raw = prefs.getString(keyFor(did), null) ?: return null
        return runCatching {
            val parts = raw.split(':', limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            val dto = AtProtoJson.decodeFromString<EncryptedTokensPayload>(plaintext)
            SessionTokens(accessJwt = dto.accessJwt, refreshJwt = dto.refreshJwt)
        }.getOrNull()
    }

    override fun saveTokens(did: String, tokens: SessionTokens) = synchronized(lock) {
        val plaintext = AtProtoJson.encodeToString(
            EncryptedTokensPayload(accessJwt = tokens.accessJwt, refreshJwt = tokens.refreshJwt)
        )
        val (ivB64, cipherB64) = try {
            encryptPayload(plaintext)
        } catch (_: java.security.GeneralSecurityException) {
            // Key may be permanently invalidated after OS/biometric/backup changes; regenerate once.
            deleteSecretKey()
            encryptPayload(plaintext)
        }
        prefs.edit().putString(keyFor(did), "$ivB64:$cipherB64").commit()
        Unit
    }

    private fun encryptPayload(plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return ivB64 to cipherB64
    }

    private fun deleteSecretKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }

    override fun removeTokens(did: String) = synchronized(lock) {
        prefs.edit().remove(keyFor(did)).commit()
        Unit
    }

    override fun clearAll() = synchronized(lock) {
        prefs.edit().clear().commit()
        Unit
    }

    private fun keyFor(did: String): String = "tok_$did"

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * In-memory vault for unit tests where AndroidKeyStore hardware provider is absent.
 */
class InMemoryTokenVault : TokenVault {
    private val map = ConcurrentHashMap<String, SessionTokens>()

    override fun getTokens(did: String): SessionTokens? = map[did]

    override fun saveTokens(did: String, tokens: SessionTokens) {
        map[did] = tokens
    }

    override fun removeTokens(did: String) {
        map.remove(did)
    }

    override fun clearAll() {
        map.clear()
    }
}
