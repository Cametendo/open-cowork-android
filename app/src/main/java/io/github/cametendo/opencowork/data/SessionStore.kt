package io.github.cametendo.opencowork.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionDataStore by preferencesDataStore(name = "session")

/** What the user typed on the login screen, so it can be pre-filled next time. */
data class ServerForm(
    val mode: ServerAddress.Mode = ServerAddress.Mode.IP,
    val host: String = "",
    val port: String = ServerAddress.DEFAULT_PORT,
    val https: Boolean = false,
)

data class StoredSession(
    val baseUrl: String? = null,
    val token: String? = null,
    val email: String = "",
    val form: ServerForm = ServerForm(),
    val user: User? = null,
) {
    val signedIn get() = baseUrl != null && token != null
}

class SessionStore(private val context: Context) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token_enc")
        val email = stringPreferencesKey("email")
        val mode = stringPreferencesKey("server_mode")
        val host = stringPreferencesKey("server_host")
        val port = stringPreferencesKey("server_port")
        val https = booleanPreferencesKey("server_https")
        val user = stringPreferencesKey("user_json")
    }

    val session: Flow<StoredSession> = context.sessionDataStore.data.map { it.toSession() }

    suspend fun current(): StoredSession = session.first()

    private fun Preferences.toSession() = StoredSession(
        baseUrl = this[Keys.baseUrl],
        token = this[Keys.token]?.let(TokenCipher::decrypt),
        email = this[Keys.email].orEmpty(),
        form = ServerForm(
            mode = runCatching { ServerAddress.Mode.valueOf(this[Keys.mode] ?: "IP") }.getOrDefault(ServerAddress.Mode.IP),
            host = this[Keys.host].orEmpty(),
            port = this[Keys.port] ?: ServerAddress.DEFAULT_PORT,
            https = this[Keys.https] ?: false,
        ),
        user = this[Keys.user]?.let { runCatching { ApiJson.decodeFromString(User.serializer(), it) }.getOrNull() },
    )

    suspend fun saveLogin(baseUrl: String, token: String, email: String, form: ServerForm, user: User) {
        context.sessionDataStore.edit {
            it[Keys.baseUrl] = baseUrl
            it[Keys.token] = TokenCipher.encrypt(token)
            it[Keys.email] = email
            it[Keys.mode] = form.mode.name
            it[Keys.host] = form.host
            it[Keys.port] = form.port
            it[Keys.https] = form.https
            it[Keys.user] = ApiJson.encodeToString(User.serializer(), user)
        }
    }

    suspend fun saveUser(user: User) {
        context.sessionDataStore.edit { it[Keys.user] = ApiJson.encodeToString(User.serializer(), user) }
    }

    /** Sign out: forget the token but keep the server and email for the login form. */
    suspend fun clearToken() {
        context.sessionDataStore.edit {
            it.remove(Keys.token)
            it.remove(Keys.user)
        }
    }
}

/** Encrypts the session token with a key that never leaves the Android Keystore. */
object TokenCipher {
    private const val ALIAS = "open_cowork_session"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val buf = ByteBuffer.allocate(1 + iv.size + out.size).put(iv.size.toByte()).put(iv).put(out)
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val data = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()
}
