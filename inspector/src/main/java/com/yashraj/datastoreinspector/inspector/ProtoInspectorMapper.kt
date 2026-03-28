package com.yashraj.datastoreinspector.inspector

interface ProtoInspectorMapper<T> {
    fun toEntries(proto: T): List<ProtoEntry>
    fun updateField(proto: T, key: String, value: String): T
}

data class ProtoEntry(
    val key: String,
    val value: String,
    val type: String,
)
