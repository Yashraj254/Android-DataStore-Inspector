package com.yashraj.datastoreinspector.sample

import com.yashraj.datastoreinspector.inspector.ProtoEntry
import com.yashraj.datastoreinspector.inspector.ProtoInspectorMapper
import com.yashraj.datastoreinspector.sample.proto.UserPreferences

class UserPreferencesMapper : ProtoInspectorMapper<UserPreferences> {

    override fun toEntries(proto: UserPreferences) = listOf(
        ProtoEntry("username", proto.username, "String"),
        ProtoEntry("email", proto.email, "String"),
        ProtoEntry("number", proto.number, "String")
    )

    override fun updateField(proto: UserPreferences, key: String, value: String): UserPreferences {
        val builder = proto.toBuilder()
        when (key) {
            "username" -> builder.username = value
            "email" -> builder.email = value
            "number" -> builder.number = value
        }
        return builder.build()
    }
}