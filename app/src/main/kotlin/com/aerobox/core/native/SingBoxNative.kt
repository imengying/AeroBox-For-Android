package com.aerobox.core.native

object SingBoxNative {
    init {
        runCatching { System.loadLibrary("box") }
    }

    external fun startService(config: String, fd: Int): Boolean
    external fun stopService()
    external fun getVersion(): String
    external fun getTrafficStats(): LongArray
    external fun testConfig(config: String): Boolean
}
