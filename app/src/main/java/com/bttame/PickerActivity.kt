package com.bttame

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bttame.databinding.ActivityPickerBinding

class PickerActivity : AppCompatActivity() {

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            binding.empty.text = getString(R.string.perm_needed)
            return
        }

        val a = getSystemService(BluetoothManager::class.java)?.adapter
        if (a == null) { binding.empty.text = getString(R.string.no_bt); return }
        val bonded = a.bondedDevices.toList()
        if (bonded.isEmpty()) {
            binding.empty.text = getString(R.string.no_bonded)
            return
        }
        binding.empty.visibility = View.GONE

        val items = bonded.map {
            TameDevice(it.address, DeviceIcons.displayName(a, it.address, true, it.address))
        }
        binding.list.adapter = DeviceListAdapter(this, items, a, { true }, showRadio = false)
        binding.list.setOnItemClickListener { _, _, pos, _ ->
            val d = items[pos]
            setResult(RESULT_OK, Intent().apply {
                putExtra("mac", d.mac)
                putExtra("name", d.name)
            })
            finish()
        }
    }
}
