package com.yashraj.datastoreinspector.inspector.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.yashraj.datastoreinspector.inspector.DatastoreInspector
import com.yashraj.datastoreinspector.inspector.handler.PreferencesDataStoreHandler
import com.yashraj.datastoreinspector.inspector.handler.ProtoDataStoreHandler
import com.yashraj.datastoreinspector.inspector.handler.SharedPreferenceHandler
import fi.iki.elonen.NanoHTTPD

class InspectorServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "InspectorServer"
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "Request: ${session.method} ${session.uri}")
        val uri = session.uri
        val prefsHandler = SharedPreferenceHandler(context)
        return when {
            uri == "/" && session.method == Method.GET -> {
                val stream = context.assets.open("inspector.html")
                newChunkedResponse(Response.Status.OK, "text/html", stream)
            }

            uri == "/api/sharedprefs" && session.method == Method.GET -> json(prefsHandler.listAll())
            uri.startsWith("/api/sharedprefs/") && session.method == Method.GET -> json(prefsHandler.getAllWithTypes(uri.removePrefix("/api/sharedprefs/")))
            uri.startsWith("/api/sharedprefs/") && session.method == Method.PUT -> handleSharedPrefsPut(
                session, uri.removePrefix("/api/sharedprefs/"), prefsHandler
            )

            uri.startsWith("/api/sharedprefs/") && session.method == Method.DELETE ->
                handleSharedPrefsDelete(session, uri.removePrefix("/api/sharedprefs/"), prefsHandler)

            uri.startsWith("/api/clear/sharedprefs/") && session.method == Method.POST -> {
                prefsHandler.clear(uri.removePrefix("/api/clear/sharedprefs/"))
                json(emptyMap<String, String>())
            }

            uri == "/api/datastore" && session.method == Method.GET ->
                json(PreferencesDataStoreHandler(DatastoreInspector.getDataStores()).listDataStores())

            uri.startsWith("/api/datastore/") && session.method == Method.GET ->
                json(PreferencesDataStoreHandler(DatastoreInspector.getDataStores()).getAll(uri.removePrefix("/api/datastore/")))

            uri.startsWith("/api/datastore/") && session.method == Method.PUT ->
                handleDataStorePut(session, uri.removePrefix("/api/datastore/"))

            uri.startsWith("/api/datastore/") && session.method == Method.DELETE ->
                handleDataStoreDelete(session, uri.removePrefix("/api/datastore/"))

            uri.startsWith("/api/clear/datastore/") && session.method == Method.POST -> {
                PreferencesDataStoreHandler(DatastoreInspector.getDataStores()).clear(uri.removePrefix("/api/clear/datastore/"))
                json(emptyMap<String, String>())
            }

            uri == "/api/proto" && session.method == Method.GET ->
                json(ProtoDataStoreHandler(DatastoreInspector.getProtoDataStores()).listProtoStores())

            uri.startsWith("/api/proto/") && session.method == Method.GET ->
                json(ProtoDataStoreHandler(DatastoreInspector.getProtoDataStores()).getAll(uri.removePrefix("/api/proto/")))

            uri.startsWith("/api/proto/") && session.method == Method.PUT ->
                handleProtoPut(session, uri.removePrefix("/api/proto/"))

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun handleSharedPrefsPut(session: IHTTPSession, name: String, handler: SharedPreferenceHandler): Response {
        val body = readBody(session) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, PutRequest::class.java)
        validateValue(req.value, req.type)?.let { return error400("Type mismatch for '${req.key}': $it") }
        handler.update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleSharedPrefsDelete(session: IHTTPSession, name: String, handler: SharedPreferenceHandler): Response {
        val body = readBody(session) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, DeleteRequest::class.java)
        handler.delete(name, req.key)
        return json(emptyMap<String, String>())
    }

    private fun handleDataStorePut(session: IHTTPSession, name: String): Response {
        val body = readBody(session) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, PutRequest::class.java)
        validateValue(req.value, req.type)?.let { return error400("Type mismatch for '${req.key}': $it") }
        PreferencesDataStoreHandler(DatastoreInspector.getDataStores()).update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleDataStoreDelete(session: IHTTPSession, name: String): Response {
        val body = readBody(session) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, TypedDeleteRequest::class.java)
        PreferencesDataStoreHandler(DatastoreInspector.getDataStores()).delete(name, req.key, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleProtoPut(session: IHTTPSession, name: String): Response {
        val body = readBody(session) ?: return error400("Missing request body")
        val req = Gson().fromJson(body, ProtoUpdateRequest::class.java)
        val handler = ProtoDataStoreHandler(DatastoreInspector.getProtoDataStores())
        // Look up the declared field type so we can validate before writing
        val fieldType = handler.getAll(name).firstOrNull { it.key == req.key }?.type
        if (fieldType != null) {
            validateValue(req.value, fieldType)?.let { return error400("Type mismatch for '${req.key}': $it") }
        }
        handler.update(name, req.key, req.value)
        return json(emptyMap<String, String>())
    }


    /**
     * Returns a human-readable error string if [value] cannot be parsed as [type], null if valid.
     * String, StringSet, and Enum types are not validated here — they accept any string input.
     */
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
        newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json",
            Gson().toJson(mapOf("error" to message)))

    private fun readBody(session: IHTTPSession): String? {
        val len = session.headers["content-length"]?.toIntOrNull() ?: return null
        if (len == 0) return null
        val buf = ByteArray(len)
        var offset = 0
        while (offset < len) {
            val read = session.inputStream.read(buf, offset, len - offset)
            if (read == -1) break
            offset += read
        }
        return String(buf, 0, offset)
    }

    private fun json(data: Any): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", Gson().toJson(data))

    private data class PutRequest(val key: String, val value: String, val type: String)

    private data class ProtoUpdateRequest(val key: String, val value: String)

    private data class DeleteRequest(val key: String)
    private data class TypedDeleteRequest(val key: String, val type: String)
}