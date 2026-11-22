/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123

import android.content.Context
import androidx.room.Room
import io.github.azx8788a.pan123.api.PanApi
import io.github.azx8788a.pan123.data.*
import io.github.azx8788a.pan123.service.*
import io.github.azx8788a.pan123.tasks.TransferManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

class AppGraph(context: Context) {
    private val applicationContext = context.applicationContext
    val accountStore = AccountStore(applicationContext)
    val settings = AppSettings(applicationContext)
    val storage = NativeStorage(applicationContext)
    val database = Room.databaseBuilder(applicationContext, TransferDatabase::class.java, "transfers.db").build()
    val api = PanApi({ accountStore.session.value }, { accountStore.loginUuid })
    val auth = AuthRepository(api, accountStore)
    val files = FileRepository(api)
    val transferHttp = TransferHttp()
    val downloads = DownloadEngine(api, transferHttp)
    val uploads = UploadEngine(api, transferHttp)
    val taskLocks = ConcurrentHashMap<String, Mutex>()
    val gate = TransferGate { settings.state.value.concurrentTasks }
    val transfers by lazy { TransferManager(applicationContext, this) }
}

class TransferGate(private val limit: () -> Int) {
    private var active = 0
    private fun acquire(): Boolean = synchronized(this) {
        if (active < limit()) { active++; true } else false
    }
    suspend fun <T> withPermit(block: suspend () -> T): T {
        while (!acquire()) delay(250)
        try { return block() } finally { synchronized(this) { active-- } }
    }
}
