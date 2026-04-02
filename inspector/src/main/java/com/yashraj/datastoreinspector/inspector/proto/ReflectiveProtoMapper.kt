package com.yashraj.datastoreinspector.inspector.proto

import java.lang.reflect.Modifier

class ReflectiveProtoMapper<T : Any> : ProtoInspectorMapper<T> {

    override fun toEntries(proto: T): List<ProtoEntry> {
        return proto.javaClass.methods
            .filter { method ->
                val name = method.name
                val returnType = method.returnType
                name.startsWith("get")
                        && method.parameterCount == 0
                        && !Modifier.isStatic(method.modifiers)
                        && !name.endsWith("Bytes")
                        && !name.endsWith("Count")
                        && !name.endsWith("List")
                        && name != "getClass"
                        && name != "getDefaultInstanceForType"
                        && name != "getSerializedSize"
                        && name != "getAllFields"
                        && name != "getUnknownFields"
                        && !returnType.name.contains("Builder")
                        && !returnType.name.contains("ByteString")
                        && !returnType.name.contains("Descriptor")
                        && !returnType.name.contains("Parser")
            }
            .mapNotNull { method ->
                val value = runCatching { method.invoke(proto) }.getOrNull() ?: return@mapNotNull null
                val fieldName = method.name.removePrefix("get").let { camelToSnake(it) }
                ProtoEntry(fieldName, value.toString(), inferType(value))
            }
    }

    override fun updateField(proto: T, key: String, value: String): T {
        val setterName = "set" + key.split("_").joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        val builder = proto.javaClass.getMethod("toBuilder").invoke(proto)
        val setter = builder.javaClass.methods.firstOrNull { it.name == setterName } ?: return proto
        val paramType = setter.parameterTypes[0]
        val typedValue: Any = when {
            paramType == String::class.java -> value
            paramType == Boolean::class.java -> value.toBoolean()
            paramType == Int::class.java -> value.toInt()
            paramType == Long::class.java -> value.toLong()
            paramType == Float::class.java -> value.toFloat()
            paramType == Double::class.java -> value.toDouble()
            paramType.isEnum -> paramType.enumConstants.first { (it as Enum<*>).name == value }
            else -> return proto
        }
        setter.invoke(builder, typedValue)
        @Suppress("UNCHECKED_CAST")
        return builder.javaClass.getMethod("build").invoke(builder) as T
    }

    private fun camelToSnake(name: String): String =
        name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase()

    private fun inferType(value: Any): String = when (value) {
        is String -> "String"
        is Boolean -> "Boolean"
        is Int -> "Int"
        is Long -> "Long"
        is Float -> "Float"
        is Double -> "Double"
        else -> if (value.javaClass.isEnum) "Enum" else "String"
    }
}
