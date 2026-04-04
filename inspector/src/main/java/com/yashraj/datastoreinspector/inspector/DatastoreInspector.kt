package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yashraj.datastoreinspector.inspector.proto.ProtoDataStoreHolder
import com.yashraj.datastoreinspector.inspector.proto.ProtoInspectorMapper
import com.yashraj.datastoreinspector.inspector.proto.ReflectiveProtoMapper
import com.yashraj.datastoreinspector.inspector.server.InspectorServer
import java.net.BindException

object DatastoreInspector {

    private const val TAG = "DatastoreInspector"

    private val registeredDataStores = mutableMapOf<String, DataStore<Preferences>>()
    private val registeredProtoDataStores = mutableMapOf<String, ProtoDataStoreHolder<*>>()
    private var server: InspectorServer? = null
    private var isRunning = false

    // Register a DataStore instance for inspection
    fun registerDataStore(name: String, dataStore: DataStore<Preferences>): DatastoreInspector {
        registeredDataStores[name] = dataStore
        Log.d(TAG, "Registered DataStore: $name")
        return this
    }

    // Register a Proto DataStore. Pass a custom mapper to override field read/write behavior; defaults to reflection.
    fun <T : Any> registerProto(
        name: String,
        dataStore: DataStore<T>,
        mapper: ProtoInspectorMapper<T>? = null
    ): DatastoreInspector {
        registeredProtoDataStores[name] = ProtoDataStoreHolder(dataStore, mapper ?: ReflectiveProtoMapper())
        Log.d(TAG, "Registered Proto DataStore: $name")
        return this
    }

    // Start the inspector. Auto-started via ContentProvider; call manually only to use a custom port.
    fun start(context: Context, port: Int = 3000) {
        if (isRunning) {
            Log.w(TAG, "Inspector already started")
            return
        }
        try {
            server = InspectorServer(context.applicationContext, port)
            server?.start()
            isRunning = true
            Toast.makeText(context.applicationContext, "Datastore Inspector started on port $port", Toast.LENGTH_SHORT).show()
        } catch (e: BindException) {
            Toast.makeText(context.applicationContext, "Port $port is already in use", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Port $port is already in use. Please choose a different port.")
            return
        }

        Log.d(TAG, "Server started on port $port")
    }

    fun stop() {
        server?.stop()
        server = null
        isRunning = false
        Log.d(TAG, "Inspector stopped")
    }

    internal fun getDataStores(): Map<String, DataStore<Preferences>> = registeredDataStores

    internal fun getProtoDataStores(): Map<String, ProtoDataStoreHolder<*>> = registeredProtoDataStores

}
