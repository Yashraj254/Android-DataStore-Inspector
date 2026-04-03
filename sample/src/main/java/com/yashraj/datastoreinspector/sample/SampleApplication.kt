package com.yashraj.datastoreinspector.sample

import android.app.Application
import com.yashraj.datastoreinspector.inspector.DatastoreInspector

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        readDataStores()
    }

    fun readDataStores() {
        DatastoreInspector.start(this)
        DatastoreInspector.register("user_preferences", prefsDataStore)
            // Use one of the two below:
            .registerProto("user_prefs", protoDataStore, UserPreferencesProtoMapper()) // custom mapper — explicit fields, no reflection
//          .registerProto("user_prefs", protoDataStore)                               // default mapper — uses reflection to discover fields
    }
}
