package com.example.speedmeter

import android.app.Application
import com.example.speedmeter.update.UpdateNotifications
import com.example.speedmeter.update.UpdateWorker

class SpeedMeterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateNotifications.createChannel(this)
        UpdateWorker.sync(this)
    }
}
