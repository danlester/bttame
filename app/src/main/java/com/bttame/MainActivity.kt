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
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bttame.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val store by lazy { DeviceStore(this) }
    private val adapter by lazy { getSystemService(BluetoothManager::class.java)?.adapter }

    private var devices: List<TameDevice> = emptyList()

    private val requestPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) reload() else binding.status.text = getString(R.string.perm_denied)
    }

    private val pickDevice = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val mac = result.data?.getStringExtra("mac") ?: return@registerForActivityResult
            val name = result.data?.getStringExtra("name") ?: mac
            store.add(TameDevice(mac, name))
            store.setActive(mac)
            reload()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> refreshStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.list.setOnItemClickListener { _, _, pos, _ ->
            store.setActive(devices[pos].mac)
            refreshStatus()
        }
        binding.connectBtn.setOnClickListener { onConnectClicked() }
        binding.forgetBtn.setOnClickListener { onForgetClicked() }
        binding.removeBtn.setOnClickListener { onRemoveClicked() }
        binding.addBtn.setOnClickListener {
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
        reload()
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

    private fun reload() {
        devices = store.list()
        if (devices.isEmpty()) {
            binding.emptyLabel.visibility = View.VISIBLE
            binding.list.visibility = View.GONE
        } else {
            binding.emptyLabel.visibility = View.GONE
            binding.list.visibility = View.VISIBLE
            val labels = devices.map { "${it.name}\n${it.mac}" }
            binding.list.adapter = ArrayAdapter(
                this, android.R.layout.simple_list_item_single_choice, labels
            )
            binding.list.choiceMode = ListView.CHOICE_MODE_SINGLE
            val activeIdx = devices.indexOfFirst { it.mac == store.activeMac() }
            if (activeIdx >= 0) binding.list.setItemChecked(activeIdx, true)
        }
        refreshStatus()
    }

    private fun activeDevice(): TameDevice? =
        store.activeMac()?.let { mac -> devices.firstOrNull { it.mac == mac } }

    private fun refreshStatus() {
        val active = activeDevice()
        if (active == null) {
            binding.status.text =
                if (devices.isEmpty()) "" else getString(R.string.not_selected)
            binding.connectBtn.isEnabled = false
            binding.forgetBtn.isEnabled = false
            binding.removeBtn.isEnabled = false
            return
        }
        binding.removeBtn.isEnabled = true
        if (!hasBtPerm()) {
            binding.status.text = getString(R.string.perm_needed)
            binding.connectBtn.isEnabled = false
            binding.forgetBtn.isEnabled = false
            return
        }
        val a = adapter
        if (a == null || !a.isEnabled) {
            binding.status.text = getString(R.string.bt_off)
            binding.connectBtn.isEnabled = false
            binding.forgetBtn.isEnabled = false
            return
        }
        val device = a.getRemoteDevice(active.mac)
        val state = when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> getString(R.string.bonded)
            BluetoothDevice.BOND_BONDING -> getString(R.string.bonding)
            else -> getString(R.string.not_bonded)
        }
        binding.status.text = "${active.name}: $state"
        binding.connectBtn.isEnabled = true
        binding.forgetBtn.isEnabled = device.bondState == BluetoothDevice.BOND_BONDED
    }

    private fun onConnectClicked() {
        if (!hasBtPerm()) { ensurePermission(); return }
        val active = activeDevice() ?: return
        val a = adapter ?: run { binding.status.text = getString(R.string.no_bt); return }
        if (!a.isEnabled) { binding.status.text = getString(R.string.bt_off); return }
        try {
            val device = a.getRemoteDevice(active.mac)
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
        val active = activeDevice() ?: return
        val a = adapter ?: return
        try {
            val device = a.getRemoteDevice(active.mac)
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
            binding.status.text = getString(R.string.forgetting)
        } catch (t: Throwable) {
            binding.status.text = "Forget failed: ${t.message}"
        }
    }

    private fun onRemoveClicked() {
        val active = activeDevice() ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_confirm_title, active.name))
            .setMessage(R.string.remove_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove_from_list) { _, _ ->
                store.remove(active.mac)
                reload()
            }
            .show()
    }
}
