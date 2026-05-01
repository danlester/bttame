package com.ideonate.whistle

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView

class DeviceListAdapter(
    private val ctx: Context,
    private val items: List<TameDevice>,
    private val btAdapter: BluetoothAdapter?,
    private val hasPerm: () -> Boolean,
    private val showRadio: Boolean = false
) : BaseAdapter() {

    var activeMac: String? = null

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): TameDevice = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.list_item_device, parent, false)
        val item = items[position]
        val granted = hasPerm()
        view.findViewById<ImageView>(R.id.icon)
            .setImageResource(DeviceIcons.iconFor(btAdapter, item.mac, granted))
        view.findViewById<TextView>(R.id.name).text =
            DeviceIcons.displayName(btAdapter, item.mac, granted, item.name)
        view.findViewById<TextView>(R.id.mac).text = item.mac
        val radio = view.findViewById<RadioButton>(R.id.radio)
        if (showRadio) {
            radio.visibility = View.VISIBLE
            radio.isChecked = item.mac.equals(activeMac, ignoreCase = true)
        } else {
            radio.visibility = View.GONE
        }
        return view
    }
}
