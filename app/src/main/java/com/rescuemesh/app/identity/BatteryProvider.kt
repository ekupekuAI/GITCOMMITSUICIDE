package com.rescuemesh.app.identity

import android.content.Context
import android.os.BatteryManager

class BatteryProvider(private val context: Context) {
    fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
