package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.util.Log

object DatastoreInspector {

    private const val TAG = "DatastoreInspector"

    internal var appContext: Context? = null
        private set

    // Start the inspector. Called automatically via ContentProvider.
    fun start(context: Context) {
        if (appContext != null) {
            Log.d(TAG, "Inspector already initialized")
            return
        }

        appContext = context.applicationContext
        Log.i(TAG, "Inspector initialized")
    }


    // Read all SharedPreferences and their current values.
    fun readAllSharedPreferences(): Map<String, List<PreferenceEntry>> {
        val context = appContext ?: return emptyMap()
        val handler = SharedPreferenceHandler(context)
        return handler.listAll().associateWith { fileName ->
            handler.getAllWithTypes(fileName)
        }
    }
}
