package com.bttame

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bttame.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("bttame", Context.MODE_PRIVATE) }
    private val adapter by lazy { getSystemService(BluetoothManager::class.java)?.adapter }

    private val requestPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refresh() else binding.status.text = getString(R.string.perm_denied)
    }

    private val pickDevice = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val mac = result.data?.getStringExtra("mac") ?: return@registerForActivityResult
            val name = result.data?.getStringExtra("name") ?: mac
            prefs.edit().putString("mac", mac).putString("name", name).apply()
            refresh()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.connectBtn.setOnClickListener { onConnectClicked() }
        binding.forgetBtn.setOnClickListener { onForgetClicked() }
        binding.changeBtn.setOnClickListener {
            pickDevice.launch(Intent(this, PickerActivity::class.java))
        }

        ensurePermission()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(btReceiver, filter)
        refresh()
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(btReceiver) }
    }

    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    private fun hasBtPerm(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun onConnectClicked() {
        if (!hasBtPerm()) { ensurePermission(); return }
        val mac = prefs.getString("mac", null)
        if (mac == null) {
            pickDevice.launch(Intent(this, PickerActivity::class.java)); return
        }
        val a = adapter ?: run { binding.status.text = getString(R.string.no_bt); return }
        if (!a.isEnabled) { binding.status.text = getString(R.string.bt_off); return }
        try {
            val device = a.getRemoteDevice(mac)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                binding.status.text = getString(R.string.already_paired)
            } else {
                val ok = createBondBredr(device)
                binding.status.text =
                    if (ok) getString(R.string.pairing_started)
                    else getString(R.string.pairing_rejected)
            }
        } catch (t: Throwable) {
            binding.status.text = "Error: ${t.message}"
        }
    }

    private fun createBondBredr(device: BluetoothDevice): Boolean {
        runCatching {
            val m = device.javaClass.getMethod("createBond", Int::class.javaPrimitiveType)
            return m.invoke(device, 1 /* TRANSPORT_BREDR */) as Boolean
        }
        return device.createBond()
    }

    private fun onForgetClicked() {
        if (!hasBtPerm()) { ensurePermission(); return }
        val mac = prefs.getString("mac", null) ?: return
        val a = adapter ?: return
        try {
            val device = a.getRemoteDevice(mac)
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
            binding.status.text = getString(R.string.forgetting)
        } catch (t: Throwable) {
            binding.status.text = "Forget failed: ${t.message}"
        }
    }

    private fun refresh() {
        val mac = prefs.getString("mac", null)
        val name = prefs.getString("name", null)
        if (mac == null) {
            binding.deviceLabel.text = getString(R.string.no_device)
            binding.status.text = getString(R.string.tap_change)
            binding.forgetBtn.isEnabled = false
            return
        }
        binding.deviceLabel.text = "$name\n$mac"
        if (!hasBtPerm()) { binding.status.text = getString(R.string.perm_needed); return }
        val a = adapter
        if (a == null || !a.isEnabled) { binding.status.text = getString(R.string.bt_off); return }
        val device = a.getRemoteDevice(mac)
        binding.status.text = when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> getString(R.string.bonded)
            BluetoothDevice.BOND_BONDING -> getString(R.string.bonding)
            else -> getString(R.string.not_bonded)
        }
        binding.forgetBtn.isEnabled = device.bondState == BluetoothDevice.BOND_BONDED
    }
}
