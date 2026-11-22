/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.azx8788a.pan123.api.AccountSession
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AccountStore(context: Context) {
    private val preferences = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val accounts = readAccounts().toMutableMap()
    private val state = MutableStateFlow(accounts[preferences.getString("active", "")])
    private val namesState = MutableStateFlow(accounts.keys.toList())
    val session = state.asStateFlow()
    val names = namesState.asStateFlow()
    val loginUuid: String = preferences.getString("device_uuid", null) ?: UUID.randomUUID().toString().replace("-", "").also {
        preferences.edit().putString("device_uuid", it).apply()
    }

    @Synchronized fun save(session: AccountSession) {
        accounts[session.accountKey] = session
        state.value = session
        namesState.value = accounts.keys.toList()
        preferences.edit().putString("active", session.accountKey).apply()
        persist() // A persistence failure must not prevent the current in-memory login.
    }

    @Synchronized fun select(key: String) {
        state.value = accounts[key] ?: return
        preferences.edit().putString("active", key).apply()
    }

    @Synchronized fun remove(key: String) {
        accounts.remove(key)
        namesState.value = accounts.keys.toList()
        persist()
    }

    @Synchronized fun logout() {
        state.value?.accountKey?.let { accounts.remove(it) }
        state.value = null
        namesState.value = accounts.keys.toList()
        preferences.edit().remove("active").apply()
        persist()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("pan123_accounts", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("pan123_accounts", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun persist() {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(json.encodeToString(accounts).toByteArray(Charsets.UTF_8))
            preferences.edit().putString("ciphertext", Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
        } catch (_: Exception) {
            preferences.edit().remove("ciphertext").apply()
        }
    }

    private fun readAccounts(): Map<String, AccountSession> = try {
        val saved = preferences.getString("ciphertext", null)
        if (saved == null) emptyMap() else {
            val bytes = Base64.decode(saved, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            json.decodeFromString<Map<String, AccountSession>>(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        }
    } catch (_: Exception) { emptyMap() }
}
