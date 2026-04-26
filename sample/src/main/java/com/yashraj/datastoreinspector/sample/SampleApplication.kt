package com.yashraj.datastoreinspector.sample

import android.app.Application
import com.yashraj.datastoreinspector.inspector.DataStoreInspector

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
//      DataStoreInspector.start(this, 3000) // optional if using the App Startup auto-start else call manually to specify a custom port
        DataStoreInspector.registerDataStore("user_preferences", prefsDataStore)
            // Use one of the two below:
            .registerProto("user_prefs", protoDataStore, UserPreferencesProtoMapper()) // custom mapper — explicit fields, no reflection
//          .registerProto("user_prefs", protoDataStore) // default mapper — uses reflection to discover fields
    }

}
