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
import androidx.annotation.UiThread
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
    /** Ports tried, in order, when no port is passed to [start]. */
    private val DEFAULT_PORTS = DEFAULT_PORT..DEFAULT_PORT + 9

    private val registeredDataStores = ConcurrentHashMap<String, DataStore<Preferences>>()
    private val registeredProtoDataStores = ConcurrentHashMap<String, ProtoDataStoreHolder<*>>()
    private var server: InspectorServer? = null
    private var isRunning = false
    private var currentPort: Int = -1
    private var startedOnDefaultRange = false

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
     * With the default port, the first free port in 5050..5059 is used, so several debuggable
     * apps on one device each get their own server; the chosen port is shown in a toast and in
     * logcat. An explicitly passed [port] is used as-is.
     *
     * The library auto-starts via App Startup before `Application.onCreate()`. Calling this with
     * a custom [port] stops the auto-started server and rebinds on the requested port. If the
     * server was already started on a custom port, a call with a different port is treated as a
     * conflict and ignored with a warning.
     */
    @Synchronized
    @UiThread
    fun start(context: Context, port: Int = DEFAULT_PORT) {
        val appContext = context.applicationContext
        // Refuse to run in non-debuggable builds. The inspector exposes full read/write access
        // to every registered DataStore and SharedPreferences, so a release APK must never start
        // a server even if a consumer accidentally calls start() or leaves the auto-initializer in.
        if ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.i(TAG, "Inspector not started: app is not debuggable.")
            return
        }
        val candidates = if (port == DEFAULT_PORT) DEFAULT_PORTS.toList() else listOf(port)
        if (isRunning) {
            // Already running on a port this call would accept.
            if (currentPort in candidates) return
            if (!startedOnDefaultRange) {
                Log.w(TAG, "Inspector already running on port $currentPort; ignoring start(port=$port).")
                return
            }
        }
        // Bind the new port before releasing the current one, so a port that turns out to be in
        // use leaves the running server untouched.
        val started = startOnFirstFreePort(candidates) { p ->
            InspectorServer(appContext, p).also { it.start() }
        }
        if (started == null) {
            val ports = if (candidates.size == 1) "Port $port is"
            else "Ports ${candidates.first()}-${candidates.last()} are"
            if (isRunning) {
                Toast.makeText(appContext, "DataStore Inspector: $ports in use; staying on port $currentPort", Toast.LENGTH_LONG).show()
                Log.w(TAG, "$ports already in use; inspector stays on port $currentPort.")
            } else {
                Toast.makeText(appContext, "DataStore Inspector: $ports already in use", Toast.LENGTH_LONG).show()
                Log.e(TAG, "$ports already in use. Pass a free port to DataStoreInspector.start().")
            }
            return
        }
        val (boundPort, boundServer) = started
        if (isRunning) {
            Log.i(TAG, "Moving inspector to port $boundPort (was $currentPort)")
            stop()
        }
        server = boundServer
        isRunning = true
        currentPort = boundPort
        startedOnDefaultRange = port == DEFAULT_PORT
        val appName = appContext.applicationInfo.loadLabel(appContext.packageManager)
        Toast.makeText(appContext, "DataStore Inspector: $appName on port $boundPort", Toast.LENGTH_SHORT).show()
        Log.i(
            TAG,
            "Inspector for $appName (${appContext.packageName}) on port $boundPort: " +
                "adb forward tcp:$boundPort tcp:$boundPort"
        )
    }

    /**
     * Stops the inspector HTTP server and releases its socket. Safe to call when the
     * server is not running. Registered DataStores are kept and will be served again
     * if [start] is called later.
     */
    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        isRunning = false
        currentPort = -1
        startedOnDefaultRange = false
        Log.d(TAG, "Inspector stopped")
    }

    internal fun getDataStores(): Map<String, DataStore<Preferences>> = registeredDataStores

    internal fun getProtoDataStores(): Map<String, ProtoDataStoreHolder<*>> = registeredProtoDataStores

}

/**
 * Calls [start] with each port in [ports] until one does not throw [BindException] (port in
 * use), returning that port and [start]'s result, or null when every port is taken. Any other
 * failure propagates.
 */
internal fun <T> startOnFirstFreePort(ports: Iterable<Int>, start: (Int) -> T): Pair<Int, T>? {
    for (port in ports) {
        try {
            return port to start(port)
        } catch (e: BindException) {
            // in use; try the next port
        }
    }
    return null
}
