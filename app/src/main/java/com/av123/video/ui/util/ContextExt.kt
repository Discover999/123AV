package com.av123.video.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** 从 Context（可能被 ContextWrapper 层层包裹，如 Compose/Library wrapper）中向上找到 Activity */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
