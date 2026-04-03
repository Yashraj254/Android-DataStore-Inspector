package com.yashraj.datastoreinspector.inspector.handler

internal fun getType(value: Any?): String = when (value) {
    is String -> "String"
    is Int -> "Int"
    is Long -> "Long"
    is Float -> "Float"
    is Double -> "Double"
    is Boolean -> "Boolean"
    is Set<*> -> "StringSet"
    null -> "Null"
    else -> "Unknown"
}
