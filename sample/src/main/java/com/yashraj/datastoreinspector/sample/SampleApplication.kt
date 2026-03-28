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
        readDataStores()
    }



    fun readDataStores() {
        DatastoreInspector.register("user_preferences", prefsDataStore)
        val dataStores = DatastoreInspector.readAllPreferencesDataStores()
        if (dataStores.isEmpty()) {
            Log.d(TAG, "No DataStores found.")
        } else {
            dataStores.forEach { (name, entries) ->
                Log.d(TAG, "DataStore: $name")
                entries.forEach { Log.d(TAG, "${it.key} : ${it.value} Type : ${it.type}") }
            }
        }
    }
}
