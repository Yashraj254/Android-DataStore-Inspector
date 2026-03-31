package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object DatastoreInspector {

    private const val TAG = "DatastoreInspector"


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

    // Register a Proto DataStore instance for inspection.
    fun <T : Any> registerProto(
        name: String,
        dataStore: DataStore<T>
    ): DatastoreInspector {
        // Use a default reflective mapper
        registeredProtoDataStores[name] = ProtoDataStoreHolder(dataStore, ReflectiveProtoMapper())
        Log.d(TAG, "Registered Proto DataStore: $name")
        return this
    }

    // Start the inspector. Called automatically via ContentProvider.
    fun start(context: Context, port: Int = 8081) {
        if (isRunning) {
            Log.w(TAG, "Inspector already started")
            return
        }
        server = InspectorServer(context.applicationContext, port)
        server?.start()
        isRunning = true
        Log.d(TAG, "Server started on port $port")
    }

//    fun stop() {
//        server?.stop()
//        scope.cancel()
//        Log.d(TAG, "Server stopped")
//    }

     internal fun getDataStores() = registeredDataStores.toMap()

     internal fun getProtoDataStores() = registeredProtoDataStores.toMap()

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


}
