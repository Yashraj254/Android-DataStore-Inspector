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

            uri == "/api/datastore" && session.method == Method.GET ->
                json(PreferencesDataStoreHandler(context).listDataStores())
            uri.startsWith("/api/datastore/") && session.method == Method.GET ->
                json(PreferencesDataStoreHandler(context).getAll(uri.removePrefix("/api/datastore/")))
            uri.startsWith("/api/datastore/") && session.method == Method.PUT ->
                handleDataStorePut(session, uri.removePrefix("/api/datastore/"))

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
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, PutRequest::class.java)
        handler.update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleDataStorePut(session: IHTTPSession, name: String): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, PutRequest::class.java)
        PreferencesDataStoreHandler(context).update(name, req.key, req.value, req.type)
        return json(emptyMap<String, String>())
    }

    private fun handleProtoPut(session: IHTTPSession, name: String): Response {
        val files = mutableMapOf<String, String>()
        session.parseBody(files)
        val body = files["postData"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing body")
        val req = Gson().fromJson(body, ProtoUpdateRequest::class.java)
        ProtoDataStoreHandler(DatastoreInspector.getProtoDataStores()).update(name, req.key, req.value)
        return json(emptyMap<String, String>())
    }

    private fun json(data: Any): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", Gson().toJson(data))

    private data class PutRequest(val key: String, val value: String, val type: String)

    private data class ProtoUpdateRequest(val key: String, val value: String)

}