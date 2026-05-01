package com.ideonate.whistle

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass

object DeviceIcons {

    @SuppressLint("MissingPermission")
    fun iconFor(adapter: BluetoothAdapter?, mac: String, hasPerm: Boolean): Int {
        if (adapter == null || !hasPerm) return R.drawable.ic_dev_bluetooth
        return try {
            val cls = adapter.getRemoteDevice(mac).bluetoothClass
                ?: return R.drawable.ic_dev_bluetooth
            iconFromClass(cls)
        } catch (t: Throwable) {
            R.drawable.ic_dev_bluetooth
        }
    }

    private fun iconFromClass(cls: BluetoothClass): Int = when (cls.deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> R.drawable.ic_dev_car
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
        BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> R.drawable.ic_dev_headset
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> R.drawable.ic_dev_speaker
        else -> when (cls.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> R.drawable.ic_dev_headset
            BluetoothClass.Device.Major.PHONE -> R.drawable.ic_dev_phone
            BluetoothClass.Device.Major.COMPUTER -> R.drawable.ic_dev_computer
            BluetoothClass.Device.Major.WEARABLE -> R.drawable.ic_dev_watch
            else -> R.drawable.ic_dev_bluetooth
        }
    }

    @SuppressLint("MissingPermission")
    fun displayName(adapter: BluetoothAdapter?, mac: String, hasPerm: Boolean, fallback: String): String {
        if (adapter == null || !hasPerm) return fallback
        return try {
            val device = adapter.getRemoteDevice(mac)
            device.alias?.takeIf { it.isNotBlank() }
                ?: device.name?.takeIf { it.isNotBlank() }
                ?: fallback
        } catch (t: Throwable) {
            fallback
        }
    }
}
