package com.yashraj.datastoreinspector.sample

import android.app.Application
import android.util.Log
import com.yashraj.datastoreinspector.inspector.DatastoreInspector

class SampleApplication : Application() {
    companion object {
        private const val TAG = "SampleApplication"
    }

    override fun onCreate() {
        super.onCreate()
        DatastoreInspector.start(this)
        val prefs = DatastoreInspector.readAllSharedPreferences()
        if (prefs.isEmpty()) {
            Log.d(TAG, "No SharedPreferences found.")
        } else {
            prefs.forEach { (fileName, entries) ->
                Log.d(TAG, "File: $fileName")
                entries.forEach { Log.d(TAG, "${it.key} : ${it.value} Type : ${it.type}") }
            }
        }
    }
}
