package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

object DatastoreInspector {

    private const val TAG = "DatastoreInspector"

    internal var appContext: Context? = null
        private set
    internal val registeredDataStores = mutableMapOf<String, DataStore<Preferences>>()
    internal val registeredProtoDataStores = mutableMapOf<String, ProtoDataStoreHolder<*>>()
    private var server: InspectorServer? = null
    private var isRunning = false

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
    fun start(context: Context, port: Int = 8081) {
        if (isRunning) {
            Log.w(TAG, "Inspector already started")
            return
        }
        appContext = context.applicationContext
        server = InspectorServer(context, port)
        server?.start()
        isRunning = true
        Log.d(TAG, "Server started on port $port")
    }

    fun stop() {
        server?.stop()
        Log.d(TAG, "Server stopped")
    }

    fun <T : Any> reflectiveProtoMapper(): ReflectiveProtoMapper<T> = ReflectiveProtoMapper()

    fun getProtoDataStores() = registeredProtoDataStores.toMap()

}
