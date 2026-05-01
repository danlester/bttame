package com.bttame

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bttame.databinding.ActivitySettingsBinding
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings by lazy { SettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderTime()

        binding.changeTimeBtn.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    settings.setForgetTime(h, m)
                    renderTime()
                    AutoForgetScheduler.reschedule(this)
                },
                settings.forgetHour(),
                settings.forgetMinute(),
                true
            ).show()
        }

        binding.cancelBondCheck.isChecked = settings.cancelBondFirst()
        binding.cancelBondCheck.setOnClickListener {
            settings.setCancelBondFirst(binding.cancelBondCheck.isChecked)
        }

        binding.runNowBtn.setOnClickListener {
            val toForget = DeviceStore(this).list().count { it.autoForget }
            AutoForgetScheduler.runNow(this)
            binding.runStatus.text = if (toForget == 0) {
                getString(R.string.run_status_none)
            } else {
                getString(R.string.run_status_fired, toForget)
            }
        }
    }

    private fun renderTime() {
        binding.timeValue.text = String.format(
            Locale.getDefault(), "%02d:%02d", settings.forgetHour(), settings.forgetMinute()
        )
    }
}
