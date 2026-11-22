/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.azx8788a.pan123

import android.app.Application

class PanApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
