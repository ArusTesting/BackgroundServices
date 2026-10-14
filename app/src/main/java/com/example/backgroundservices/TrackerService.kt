package com.example.backgroundservices

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TrackerService : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }
}