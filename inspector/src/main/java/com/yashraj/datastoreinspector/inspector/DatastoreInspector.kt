package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.collections.set

object DatastoreInspector {

    private const val TAG = "DatastoreInspector"

    internal var appContext: Context? = null
        private set
    internal val registeredDataStores = mutableMapOf<String, DataStore<Preferences>>()
    internal val registeredProtoDataStores = mutableMapOf<String, ProtoDataStoreHolder<*>>()



    // Register a DataStore instance for inspection
    fun register(name: String, dataStore: DataStore<Preferences>): DatastoreInspector {
        registeredDataStores[name] = dataStore
        Log.d(TAG, "Registered DataStore: $name")
        return this
    }

    // Register a Proto DataStore instance for inspection. Provide a mapper to define how fields are read and written.
    fun <T> registerProto(
        name: String,
        dataStore: DataStore<T>,
        mapper: ProtoInspectorMapper<T>
    ): DatastoreInspector {
        registeredProtoDataStores[name] = ProtoDataStoreHolder(dataStore, mapper)
        Log.d(TAG, "Registered Proto DataStore: $name")
        return this
    }

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

    // Read all Preferences DataStores and their current values.
    fun readAllPreferencesDataStores(): Map<String, List<DataStoreEntry>> {
        val context = appContext ?: return emptyMap()
        val handler = PreferencesDataStoreHandler(context)
        return handler.listAll().associate { info ->
            info.name to handler.getAll(info.name)
        }
    }

     fun getProtoDataStores() = registeredProtoDataStores.toMap()

}
