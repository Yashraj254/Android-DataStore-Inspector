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
            .registerProto("user_proto_preferences", protoDataStore, UserPreferencesMapper())
    }
}
