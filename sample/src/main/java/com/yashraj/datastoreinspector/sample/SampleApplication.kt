package com.yashraj.datastoreinspector.sample

import android.app.Application
import com.yashraj.datastoreinspector.inspector.DataStoreInspector

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Auto-started by App Startup. Call start() manually only to override the port.
//      DataStoreInspector.start(this, 5051)
        DataStoreInspector.registerDataStore("user_preferences", prefsDataStore)
            // Pick one registerProto() call. The explicit mapper is preferred when field
            // names matter; the no-mapper form uses reflection to discover them.
            .registerProto("user_prefs", protoDataStore, UserPreferencesProtoMapper())
        //  .registerProto("user_prefs", protoDataStore)
    }

}
