package com.lagradost

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TabiiSporPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TabiiSpor())
    }
}