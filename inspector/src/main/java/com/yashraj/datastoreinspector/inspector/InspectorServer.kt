package com.yashraj.datastoreinspector.inspector

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlin.jvm.java

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
                json(PreferencesDataStoreHandler(context).listDataStores())
            uri.startsWith("/api/datastore/") && session.method == Method.GET ->
                json(PreferencesDataStoreHandler(context).getAll(uri.removePrefix("/api/datastore/")))
            uri.startsWith("/api/datastore/") && session.method == Method.PUT ->
                handleDataStorePut(session, uri.removePrefix("/api/datastore/"))
            uri.startsWith("/api/datastore/") && session.method == Method.DELETE ->
                handleDataStoreDelete(session, uri.removePrefix("/api/datastore/"))
            uri.startsWith("/api/clear/datastore/") && session.method == Method.POST -> {
                PreferencesDataStoreHandler(context).clear(uri.removePrefix("/api/clear/datastore/"))
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
        val req = Gson().fromJson(body, InspectorServer.PutRequest::class.java)
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
        val req = Gson().fromJson(body, InspectorServer.PutRequest::class.java)
        PreferencesDataStoreHandler(context).update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleDataStoreDelete(session: IHTTPSession, name: String): Response {
        val body = readBody(session) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, TypedDeleteRequest::class.java)
        PreferencesDataStoreHandler(context).delete(name, req.key, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleProtoPut(session: IHTTPSession, name: String): Response {
        val body = readBody(session) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, InspectorServer.ProtoUpdateRequest::class.java)
        ProtoDataStoreHandler(DatastoreInspector.getProtoDataStores()).update(name, req.key, req.value)
        return json(emptyMap<String, String>())
    }

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