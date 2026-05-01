package com.ideonate.whistle

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class AutoForgetReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FORGET) return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) return

        val store = DeviceStore(context)
        var forgotten = 0
        for (d in store.list().filter { it.autoForget }) {
            runCatching {
                val device = adapter.getRemoteDevice(d.mac)
                if (device.bondState != BluetoothDevice.BOND_BONDED) return@runCatching
                if (isConnected(device)) return@runCatching
                val m = device.javaClass.getMethod("removeBond")
                m.invoke(device)
                forgotten++
            }
        }
    }

    private fun isConnected(device: BluetoothDevice): Boolean = runCatching {
        val m = device.javaClass.getMethod("isConnected")
        m.invoke(device) as Boolean
    }.getOrElse { false }

    companion object {
        const val ACTION_FORGET = "com.ideonate.whistle.AUTO_FORGET"
    }
}
