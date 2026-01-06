package com.sarapcanagii

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NeonSporPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NeonSpor())
    }
}
