package com.example.backgroundservices

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import android.content.ContentValues
import android.provider.CalendarContract
import android.widget.Toast
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    private lateinit var statusTextView: TextView
    private lateinit var btnStart: Button
    private lateinit var btnLatihan: Button
    private var requestedTrainingMode = false

    companion object {
        const val ACTION_SERVICE_STOPPED_JUMP = "com.example.backgroundservices.ACTION_SERVICE_STOPPED_JUMP"
        const val REQUEST_CODE_PERMISSIONS = 101
        const val EXTRA_JUMP_COUNT = "com.example.backgroundservices.EXTRA_JUMP_COUNT"
        const val EXTRA_TRAINING_SESSION = "com.example.backgroundservices.EXTRA_TRAINING_SESSION"
        const val REQUEST_CODE_CALENDAR = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        statusTextView = findViewById(R.id.statusTextView)
        btnStart = findViewById(R.id.btnStart)
        btnLatihan = findViewById(R.id.btnLatihan)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnStart.setOnClickListener {
            requestedTrainingMode = false
            if (hasAllPermissions()) {
                startTrackerService()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }
        btnLatihan.setOnClickListener {
            requestedTrainingMode = true
            if (hasAllPermissions()) {
                startTrackerService(true)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }
        handleIntent(intent)
    }
    private fun hasAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun startTrackerService(trainingMode: Boolean = false) {
        val serviceIntent = Intent(this, TrackerService::class.java).apply {
            putExtra(TrackerService.EXTRA_TRAINING_MODE, trainingMode)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        statusTextView.text = if (trainingMode) {
            "Latihan sedang berjalan"
        } else {
            "Background service sedang berjalan"
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                val serviceIntent = Intent(this, TrackerService::class.java).apply {
                    putExtra(TrackerService.EXTRA_TRAINING_MODE, requestedTrainingMode)
                }
                startForegroundService(serviceIntent)
                finish()
            } else {
                statusTextView.text = "Izin ditolak, service tidak dapat dijalankan."
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_SERVICE_STOPPED_JUMP) return
        val count = intent.getIntExtra(EXTRA_JUMP_COUNT, 0)
        val sessionId = intent.getLongExtra(EXTRA_TRAINING_SESSION, 0L)
        if (count >= 3 && sessionId != 0L) {
            statusTextView.text = "Latihan selesai! Jumlah lompatan: $count"
            saveTrainingCalendarEvent(count, sessionId)
        } else {
            statusTextView.text = "Background services sudah berhenti karena Anda melompat"
        }
    }
    private fun hasCalendarPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun saveTrainingCalendarEvent(count: Int, sessionId: Long) {
        val preferences = getSharedPreferences("latihan_events", MODE_PRIVATE)
        val eventKey = "saved_$sessionId"
        if (preferences.getBoolean(eventKey, false)) return
        if (!hasCalendarPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                ),
                REQUEST_CODE_CALENDAR
            )
            return
        }
        try {
            val calendarId = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }

            if (calendarId == null) {
                Toast.makeText(this, "Tidak ada kalender yang dapat digunakan", Toast.LENGTH_LONG).show()
                return
            }

            val startTime = System.currentTimeMillis()
            val endTime = startTime + 30 * 60 * 1000L

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "Latihan")
                put(CalendarContract.Events.DESCRIPTION, "Jumlah lompatan: $count")
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val eventUri = contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values
            )
            if (eventUri != null) {
                preferences.edit().putBoolean(eventKey, true).apply()
                Toast.makeText(this, "Event Latihan berhasil dibuat", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gagal membuat event", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengakses kalender: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}