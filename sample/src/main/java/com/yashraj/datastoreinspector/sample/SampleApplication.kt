package com.yashraj.datastoreinspector.sample

import android.app.Application
import com.yashraj.datastoreinspector.inspector.DataStoreInspector

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Auto-started by App Startup. Call start() manually only to override the port.
//      DataStoreInspector.start(this, 5051)
        DataStoreInspector.registerDataStore("user_preferences", prefsDataStore)
            // Pick one registerProto() call. The no-mapper form uses reflection to discover
            // every field — including nested messages, repeated fields and enums — and is what
            // most apps want. Provide an explicit mapper only when you need to control which
            // fields are exposed or how their values are parsed.
            .registerProto("user_prefs", protoDataStore)
        //  .registerProto("user_prefs", protoDataStore, UserPreferencesProtoMapper())
    }

}
