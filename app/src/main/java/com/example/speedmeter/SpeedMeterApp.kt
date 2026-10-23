package com.example.speedmeter

import android.app.Application
import com.example.speedmeter.update.UpdateNotifications
import com.example.speedmeter.update.UpdateWorker

class SpeedMeterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.init(this)
        DiagnosticsLog.log("app process started")
        UpdateNotifications.createChannel(this)
        UpdateWorker.sync(this)
    }
}
