/*
 * Copyright (C) 2026 Yashraj Singh Jadon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.yashraj.datastoreinspector.inspector.proto.ProtoDataStoreHolder
import com.yashraj.datastoreinspector.inspector.proto.ProtoInspectorMapper
import com.yashraj.datastoreinspector.inspector.proto.ReflectiveProtoMapper
import com.yashraj.datastoreinspector.inspector.server.InspectorServer
import java.net.BindException
import java.util.concurrent.ConcurrentHashMap

object DataStoreInspector {

    private const val TAG = "DataStoreInspector"
    private const val DEFAULT_PORT = 5050

    private val registeredDataStores = ConcurrentHashMap<String, DataStore<Preferences>>()
    private val registeredProtoDataStores = ConcurrentHashMap<String, ProtoDataStoreHolder<*>>()
    private var server: InspectorServer? = null
    private var isRunning = false
    private var currentPort: Int = -1

    /**
     * Registers a Preferences DataStore for inspection under [name].
     *
     * Returns this object so calls can be chained.
     */
    fun registerDataStore(name: String, dataStore: DataStore<Preferences>): DataStoreInspector {
        registeredDataStores[name] = dataStore
        Log.d(TAG, "Registered DataStore: $name")
        return this
    }

    /**
     * Registers a Proto DataStore for inspection under [name].
     *
     * If [mapper] is null, fields are read and written via reflection over the generated
     * proto class. Provide a [ProtoInspectorMapper] to control the exposed field set or
     * to support proto types that the reflective mapper cannot introspect.
     */
    fun <T : Any> registerProto(
        name: String,
        dataStore: DataStore<T>,
        mapper: ProtoInspectorMapper<T>? = null
    ): DataStoreInspector {
        registeredProtoDataStores[name] = ProtoDataStoreHolder(dataStore, mapper ?: ReflectiveProtoMapper())
        Log.d(TAG, "Registered Proto DataStore: $name")
        return this
    }

    /**
     * Starts the inspector HTTP server on [port] (default 5050).
     *
     * The library auto-starts via App Startup before `Application.onCreate()`, so by the time
     * your code runs the server is already bound to port 5050. Calling this with a custom
     * [port] will stop the auto-started server and rebind on the requested port. If the
     * server has already been moved to a non-default port, a subsequent call with a different
     * port is treated as a conflict and ignored with a warning.
     */
    fun start(context: Context, port: Int = DEFAULT_PORT) {
        val appContext = context.applicationContext
        // Refuse to run in non-debuggable builds. The inspector exposes full read/write access
        // to every registered DataStore and SharedPreferences, so a release APK must never start
        // a server even if a consumer accidentally calls start() or leaves the auto-initializer in.
        if ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.i(TAG, "Inspector not started: app is not debuggable.")
            return
        }
        if (isRunning) {
            if (port == currentPort) return
            if (currentPort == DEFAULT_PORT) {
                Log.i(TAG, "Restarting inspector on port $port (was $currentPort)")
                stop()
                // fall through to start on the new port
            } else {
                Log.w(
                    TAG,
                    "Inspector already running on port $currentPort; ignoring start(port=$port)."
                )
                return
            }
        }
        try {
            server = InspectorServer(appContext, port)
            server?.start()
            isRunning = true
            currentPort = port
            Toast.makeText(appContext, "DataStore Inspector started on port $port", Toast.LENGTH_SHORT).show()
        } catch (e: BindException) {
            Toast.makeText(appContext, "Port $port is already in use", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Port $port is already in use. Please choose a different port.")
            return
        }

        Log.d(TAG, "Server started on port $port")
    }

    /**
     * Stops the inspector HTTP server and releases its socket. Safe to call when the
     * server is not running. Registered DataStores are kept and will be served again
     * if [start] is called later.
     */
    fun stop() {
        server?.stop()
        server = null
        isRunning = false
        currentPort = -1
        Log.d(TAG, "Inspector stopped")
    }

    internal fun getDataStores(): Map<String, DataStore<Preferences>> = registeredDataStores

    internal fun getProtoDataStores(): Map<String, ProtoDataStoreHolder<*>> = registeredProtoDataStores

}
