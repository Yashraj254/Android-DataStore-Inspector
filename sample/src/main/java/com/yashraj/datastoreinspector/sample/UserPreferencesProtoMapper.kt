package com.yashraj.datastoreinspector.sample

import com.yashraj.datastoreinspector.inspector.model.ProtoEntry
import com.yashraj.datastoreinspector.inspector.proto.ProtoInspectorMapper
import com.yashraj.datastoreinspector.sample.proto.UserPreferences

// Custom mapper for UserPreferences — no reflection, fields are read/written directly.
// Use this instead of ReflectiveProtoMapper when you want predictable field names,
class UserPreferencesProtoMapper : ProtoInspectorMapper<UserPreferences> {

    override fun toEntries(proto: UserPreferences): List<ProtoEntry> = listOf(
        ProtoEntry("username", proto.username, "String"),
        ProtoEntry("email", proto.email, "String"),
        ProtoEntry("number", proto.number, "String"),
        ProtoEntry("age", proto.age.toString(), "Int"),
        ProtoEntry("is_premium", proto.isPremium.toString(), "Boolean"),
        ProtoEntry("rating", proto.rating.toString(), "Float"),
        ProtoEntry("last_login", proto.lastLogin.toString(), "Long"),
        ProtoEntry("account_balance", proto.accountBalance.toString(), "Double"),
    )

    override fun updateField(proto: UserPreferences, key: String, value: String): UserPreferences {
        val builder = proto.toBuilder()
        when (key) {
            "username" -> builder.username = value
            "email" -> builder.email = value
            "number" -> builder.number = value
            "age" -> builder.age = value.toInt()
            "is_premium" -> builder.isPremium = value.toBoolean()
            "rating" -> builder.rating = value.toFloat()
            "last_login" -> builder.lastLogin = value.toLong()
            "account_balance" -> builder.accountBalance = value.toDouble()
        }
        return builder.build()
    }
}
