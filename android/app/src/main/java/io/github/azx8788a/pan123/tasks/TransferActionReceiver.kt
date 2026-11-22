/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.azx8788a.pan123.PanApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransferActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("task_id") ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { (context.applicationContext as PanApplication).graph.transfers.pause(id) }
            finally { result.finish() }
        }
    }
}
