/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.service

import android.os.Build
import io.github.azx8788a.pan123.api.*
import io.github.azx8788a.pan123.data.AccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val api: PanApi, private val store: AccountStore) {
    val session = store.session
    val accounts = store.names

    suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotBlank()) { "请输入账号和密码" }
        val token = api.login(username.trim(), password)
        store.save(AccountSession(username.trim(), token, store.loginUuid, Build.VERSION.RELEASE))
    }

    suspend fun acceptQrToken(token: String) = withContext(Dispatchers.IO) {
        val previous = session.value
        store.save(AccountSession("扫码登录", token, store.loginUuid, Build.VERSION.RELEASE))
        try {
            val info = api.profile()
            if (info.uid <= 0) throw ApiException(-1, "扫码登录后未能获取账号 ID")
            store.save(AccountSession("uid:${info.uid}", token, store.loginUuid, Build.VERSION.RELEASE))
            store.remove("扫码登录")
        } catch (error: Exception) {
            store.logout()
            if (previous != null) store.save(previous)
            throw error
        }
    }

    suspend fun profile() = api.profile()
    suspend fun qrGenerate() = api.qrGenerate()
    suspend fun qrPoll(id: String) = api.qrPoll(id)
    fun select(key: String) = store.select(key)
    fun logout() = store.logout()
}
