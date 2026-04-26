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
package com.yashraj.datastoreinspector.inspector.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.yashraj.datastoreinspector.inspector.DataStoreInspector
import com.yashraj.datastoreinspector.inspector.handler.PreferencesDataStoreHandler
import com.yashraj.datastoreinspector.inspector.handler.ProtoDataStoreHandler
import com.yashraj.datastoreinspector.inspector.handler.SharedPreferenceHandler
import kotlin.jvm.java

internal class InspectorServer(context: Context, port: Int) : SimpleHttpServer(port) {

    private val prefsHandler = SharedPreferenceHandler(context)
    private val dataStoreHandler = PreferencesDataStoreHandler(DataStoreInspector.getDataStores())
    private val protoHandler = ProtoDataStoreHandler(DataStoreInspector.getProtoDataStores())
    private val htmlBytes = context.assets.open("inspector.html").use { it.readBytes() }

    companion object {
        private const val TAG = "InspectorServer"
        private val gson = Gson()
    }

    override fun serve(request: Request): Response {
        Log.d(TAG, "Request: ${request.method} ${request.uri}")
        val uri = request.uri
        return when {
            uri == "/" && request.method == "GET" -> Response(Status.OK, "text/html", htmlBytes)

            uri == "/api/sharedprefs" && request.method == "GET" -> json(prefsHandler.listAll())
            uri.startsWith("/api/sharedprefs/") && request.method == "GET" -> json(prefsHandler.getAllWithTypes(uri.removePrefix("/api/sharedprefs/")))
            uri.startsWith("/api/sharedprefs/") && request.method == "PUT" -> handleSharedPrefsPut(
                request, uri.removePrefix("/api/sharedprefs/"), prefsHandler
            )

            uri.startsWith("/api/sharedprefs/") && request.method == "DELETE" ->
                handleSharedPrefsDelete(request, uri.removePrefix("/api/sharedprefs/"), prefsHandler)

            uri.startsWith("/api/clear/sharedprefs/") && request.method == "POST" -> {
                prefsHandler.clear(uri.removePrefix("/api/clear/sharedprefs/"))
                json(emptyMap<String, String>())
            }

            uri == "/api/datastore" && request.method == "GET" ->
                json(dataStoreHandler.listDataStores())

            uri.startsWith("/api/datastore/") && request.method == "GET" ->
                json(dataStoreHandler.getAll(uri.removePrefix("/api/datastore/")))

            uri.startsWith("/api/datastore/") && request.method == "PUT" ->
                handleDataStorePut(request, uri.removePrefix("/api/datastore/"))

            uri.startsWith("/api/datastore/") && request.method == "DELETE" ->
                handleDataStoreDelete(request, uri.removePrefix("/api/datastore/"))

            uri.startsWith("/api/clear/datastore/") && request.method == "POST" -> {
                dataStoreHandler.clear(uri.removePrefix("/api/clear/datastore/"))
                json(emptyMap<String, String>())
            }

            uri == "/api/proto" && request.method == "GET" ->
                json(protoHandler.listProtoStores())

            uri.startsWith("/api/proto/") && request.method == "GET" ->
                json(protoHandler.getAll(uri.removePrefix("/api/proto/")))

            uri.startsWith("/api/proto/") && request.method == "PUT" ->
                handleProtoPut(request, uri.removePrefix("/api/proto/"))

            else -> respond(Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun handleSharedPrefsPut(request: Request, name: String, handler: SharedPreferenceHandler): Response {
        val body = readBody(request) ?: return respond(Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = gson.fromJson(body, PutRequest::class.java)
        validateValue(req.value, req.type)?.let { return error400("Type mismatch for '${req.key}': $it") }
        handler.update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleSharedPrefsDelete(request: Request, name: String, handler: SharedPreferenceHandler): Response {
        val body = readBody(request) ?: return respond(Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = gson.fromJson(body, DeleteRequest::class.java)
        handler.delete(name, req.key)
        return json(emptyMap<String, String>())
    }

    private fun handleDataStorePut(request: Request, name: String): Response {
        val body = readBody(request) ?: return respond(Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = gson.fromJson(body, PutRequest::class.java)
        validateValue(req.value, req.type)?.let { return error400("Type mismatch for '${req.key}': $it") }
        dataStoreHandler.update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleDataStoreDelete(request: Request, name: String): Response {
        val body = readBody(request) ?: return respond(Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = gson.fromJson(body, TypedDeleteRequest::class.java)
        dataStoreHandler.delete(name, req.key, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleProtoPut(request: Request, name: String): Response {
        val body = readBody(request) ?: return error400("Missing request body")
        val req = gson.fromJson(body, ProtoUpdateRequest::class.java)
        protoHandler.update(name, req.key, req.value)
        return json(emptyMap<String, String>())
    }


    // Validate that the provided value can be parsed as the expected type
    private fun validateValue(value: String, type: String): String? = when (type) {
        "Int"     -> if (value.toIntOrNull() == null)
            "\"$value\" is not a valid Int" else null
        "Long"    -> if (value.toLongOrNull() == null)
            "\"$value\" is not a valid Long" else null
        "Float"   -> if (value.toFloatOrNull() == null)
            "\"$value\" is not a valid Float" else null
        "Double"  -> if (value.toDoubleOrNull() == null)
            "\"$value\" is not a valid Double" else null
        "Boolean" -> if (value != "true" && value != "false")
            "\"$value\" is not a valid Boolean — must be \"true\" or \"false\"" else null
        else      -> null  // String, StringSet, Enum — accept as-is
    }

    private fun error400(message: String): Response =
        respond(Status.BAD_REQUEST, "application/json",
            gson.toJson(mapOf("error" to message)))

    private fun readBody(request: Request): String? {
        val len = request.headers["content-length"]?.toIntOrNull() ?: return null
        if (len == 0) return null
        val buf = ByteArray(len)
        var offset = 0
        while (offset < len) {
            val read = request.inputStream.read(buf, offset, len - offset)
            if (read == -1) break
            offset += read
        }
        return String(buf, 0, offset)
    }

    private fun json(data: Any): Response =
        respond(Status.OK, "application/json", gson.toJson(data))

    private data class PutRequest(val key: String, val value: String, val type: String)

    private data class ProtoUpdateRequest(val key: String, val value: String)

    private data class DeleteRequest(val key: String)
    private data class TypedDeleteRequest(val key: String, val type: String)
}