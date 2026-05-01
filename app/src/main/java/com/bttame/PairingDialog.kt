package com.bttame

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PairingDialog(
    private val activity: AppCompatActivity,
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val displayName: String,
    private val createBond: (BluetoothDevice) -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null
    private var statusText: TextView? = null
    private var spinner: ProgressBar? = null
    private var registered = false
    private var dismissed = false

    private enum class Mode { CONNECTING, PROMPT, DONE }
    private var mode: Mode = Mode.CONNECTING

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val d: BluetoothDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
            if (d?.address != device.address) return
            when (intent.action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val reason = intent.getIntExtra(EXTRA_REASON, -1)
                    onBondStateChanged(state, reason)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    // Ignore the transient ACL link the OS opens during a pairing attempt;
                    // only treat ACL up as success once the bond is actually established.
                    if (device.bondState == BluetoothDevice.BOND_BONDED) showConnected()
                }
            }
        }
    }

    fun show() {
        val view = activity.layoutInflater.inflate(R.layout.dialog_pairing, null)
        statusText = view.findViewById(R.id.pairing_status)
        spinner = view.findViewById(R.id.pairing_spinner)
        view.findViewById<ImageView>(R.id.pairing_icon)
            .setImageResource(DeviceIcons.iconFor(adapter, device.address, true))
        showConnecting()

        activity.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        })
        registered = true

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pairing_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel) { _, _ -> userCancel() }
            .setOnDismissListener { teardown() }
            .setCancelable(false)
            .show()
            .also { it.setCanceledOnTouchOutside(false) }

        startInitialAttempt()
    }

    private fun startInitialAttempt() {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            // Already bonded — don't call createBond (it'd no-op or trigger a toast).
            // If the ACL is already up, we're done. Otherwise wait for ACL_CONNECTED;
            // if nothing happens within INITIAL_WAIT_MS, prompt the user to power it on.
            if (isAclConnected()) {
                showConnected()
                return
            }
            handler.postDelayed({
                if (!dismissed && mode == Mode.CONNECTING) {
                    showPrompt(activity.getString(R.string.pairing_turn_on, displayName))
                }
            }, SettingsStore(activity).initialWaitSeconds() * 1000L)
            return
        }
        attemptBond()
    }

    private fun attemptBond() {
        if (dismissed) return
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            showConnected(); return
        }
        val ok = runCatching { createBond(device) }.getOrDefault(false)
        if (!ok) {
            showPrompt(activity.getString(R.string.pairing_turn_on, displayName))
            scheduleRetry()
        }
    }

    private fun onBondStateChanged(state: Int, reason: Int) {
        if (dismissed) return
        when (state) {
            BluetoothDevice.BOND_BONDING -> {
                if (mode != Mode.PROMPT) showConnecting()
            }
            BluetoothDevice.BOND_BONDED -> showConnected()
            BluetoothDevice.BOND_NONE -> {
                when (reason) {
                    REASON_AUTH_CANCELED, REASON_REMOTE_AUTH_CANCELED -> dialog?.dismiss()
                    REASON_AUTH_FAILED, REASON_AUTH_REJECTED, REASON_REPEATED_ATTEMPTS -> {
                        showPrompt(
                            activity.getString(R.string.pairing_pairing_mode, displayName)
                        )
                        scheduleRetry()
                    }
                    else -> {
                        showPrompt(
                            activity.getString(R.string.pairing_turn_on, displayName)
                        )
                        scheduleRetry()
                    }
                }
            }
        }
    }

    private fun showConnecting() {
        mode = Mode.CONNECTING
        val text = statusText ?: return
        text.text = activity.getString(R.string.pairing_connecting, displayName)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        text.typeface = Typeface.DEFAULT
        text.setTextColor(
            MaterialColors.getColor(text, com.google.android.material.R.attr.colorOnSurface)
        )
        clearBanner(text)
        spinner?.visibility = View.VISIBLE
    }

    private fun showPrompt(message: String) {
        mode = Mode.PROMPT
        val text = statusText ?: return
        if (text.text != message) {
            text.text = message
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            text.typeface = Typeface.DEFAULT_BOLD
            text.setTextColor(
                MaterialColors.getColor(text, com.google.android.material.R.attr.colorOnPrimaryContainer)
            )
            applyBanner(
                text,
                MaterialColors.getColor(text, com.google.android.material.R.attr.colorPrimaryContainer)
            )
        }
        spinner?.visibility = View.VISIBLE
    }

    private fun showConnected() {
        if (mode == Mode.DONE) return
        mode = Mode.DONE
        val text = statusText ?: return
        text.text = activity.getString(R.string.pairing_paired)
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        text.typeface = Typeface.DEFAULT_BOLD
        text.setTextColor(
            MaterialColors.getColor(text, com.google.android.material.R.attr.colorPrimary)
        )
        clearBanner(text)
        spinner?.visibility = View.GONE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ dialog?.dismiss() }, 1200)
    }

    private fun applyBanner(text: TextView, bgColor: Int) {
        val d = text.resources.displayMetrics.density
        text.background = GradientDrawable().apply {
            cornerRadius = 14f * d
            setColor(bgColor)
        }
        val pad = (16 * d).toInt()
        text.setPadding(pad, pad, pad, pad)
    }

    private fun clearBanner(text: TextView) {
        text.background = null
        text.setPadding(0, 0, 0, 0)
    }

    private fun isAclConnected(): Boolean = runCatching {
        device.javaClass.getMethod("isConnected").invoke(device) as Boolean
    }.getOrDefault(false)

    private fun scheduleRetry() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ attemptBond() }, RETRY_DELAY_MS)
    }

    private fun userCancel() {
        runCatching {
            device.javaClass.getMethod("cancelBondProcess").invoke(device)
        }
    }

    private fun teardown() {
        dismissed = true
        handler.removeCallbacksAndMessages(null)
        if (registered) {
            runCatching { activity.unregisterReceiver(receiver) }
            registered = false
        }
    }

    companion object {
        // Hidden but stable extra/constants from BluetoothDevice.
        private const val EXTRA_REASON = "android.bluetooth.device.extra.REASON"
        private const val REASON_AUTH_FAILED = 1
        private const val REASON_AUTH_REJECTED = 2
        private const val REASON_AUTH_CANCELED = 3
        private const val REASON_REMOTE_AUTH_CANCELED = 8
        private const val REASON_REPEATED_ATTEMPTS = 7
        private const val RETRY_DELAY_MS = 2500L
    }
}
