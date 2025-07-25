package com.danil.cameraserver

import android.content.Context
import java.io.File

object AssetCopier {
    fun copyIfMissing(ctx: Context, name: String) : File {
        val dst = File(ctx.filesDir, name)
        if (!dst.exists()) {
            ctx.assets.open(name).use { it.copyTo(dst.outputStream()) }
            dst.setExecutable(true)
        }
        return dst
    }
}
