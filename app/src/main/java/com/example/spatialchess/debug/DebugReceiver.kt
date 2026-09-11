package com.example.spatialchess.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.spatialchess.app.ChessViewModel

/**
 * Test harness for the PICO Emulator, where spatial input cannot be scripted over adb:
 *
 * adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd view --es arg "35,20,1"
 * adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd select --es arg e2
 */
class DebugReceiver(private val vm: ChessViewModel) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val arg = intent.getStringExtra("arg") ?: ""
        val tag = intent.getStringExtra("tag") ?: ""
        android.util.Log.i("SpatialChess.Dbg", "received cmd=$cmd arg=$arg tag=$tag")
        vm.debug(cmd, arg)
    }

    companion object {
        const val ACTION = "com.example.spatialchess.DEBUG"
        fun register(context: Context, vm: ChessViewModel): DebugReceiver {
            val r = DebugReceiver(vm)
            context.registerReceiver(r, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
            return r
        }
    }
}
