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
package com.yashraj.datastoreinspector.inspector.proto

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.yashraj.datastoreinspector.inspector.model.ProtoEntry
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Zero-config mapper that inspects any protobuf-generated message purely through reflection on its
 * generated getters/builders — no protobuf descriptors or `JsonFormat`, so it works with
 * protobuf-javalite (what Proto DataStore generates) and stays independent of the host app's
 * protobuf version.
 *
 * Scalar and enum fields are exposed as flat values. Composite fields are
 * exposed as JSON so the inspector UI can render and edit arbitrary nesting:
 *  - a singular message field becomes a `"Message"` entry whose value is a JSON object;
 *  - a `repeated` field becomes a `"List"` entry whose value is a JSON array (editing the array
 *    length adds/removes elements);
 *  - a `map` field becomes a read-only `"Map"` entry.
 *
 * On write, the JSON is validated against a cached per-class schema and applied by rebuilding the
 * message reflectively (`newBuilder()` / `setX(..)` / `clearX()` + `addX(..)`). Invalid input
 * (unknown field, wrong type, malformed JSON) throws [IllegalArgumentException], which the server
 * surfaces as an HTTP 400.
 */
internal class ReflectiveProtoMapper<T : Any> : ProtoInspectorMapper<T> {

    private enum class Kind { SCALAR, ENUM, MESSAGE, REPEATED, MAP }

    /**
     * One inspectable field of a message. [accessorName] is the generated CamelCase name (`X` in
     * `getX()` / `setX()`), kept verbatim because the snake_case [jsonKey] cannot always be
     * converted back (`a_b_c` generates `setABC`). [getter] is the message-side accessor
     * (`getX()`, `getXList()` or `getXMap()`); [valueType] is the scalar/enum/message class, the
     * element class for [Kind.REPEATED], or the value class for [Kind.MAP]. [keyType] is set only
     * for [Kind.MAP].
     */
    private class FieldSchema(
        val accessorName: String,
        val jsonKey: String,
        val kind: Kind,
        val getter: Method,
        val valueType: Class<*>,
        val keyType: Class<*>? = null,
    )

    /**
     * A `oneof` group. [caseGetter] is the generated `getXCase()`, whose enum constant names the
     * member that is currently set (or ends in `_NOT_SET`).
     */
    private class OneofSchema(val name: String, val caseGetter: Method, val members: List<FieldSchema>) {
        /** Members other than the one currently set on [message]. */
        fun unsetMembers(message: Any): List<FieldSchema> {
            val case = (caseGetter.invoke(message) as Enum<*>).name
            return members.filter { normalize(it.jsonKey) != normalize(case) }
        }
    }

    private class MessageSchema(val fields: List<FieldSchema>, val oneofs: List<OneofSchema>) {
        val byKey: Map<String, FieldSchema> = fields.associateBy { it.jsonKey }
    }

    // Schema is derived once per proto class and cached. ConcurrentHashMap because request
    // coroutines hit getOrPut concurrently.
    private val schemaCache = ConcurrentHashMap<Class<*>, MessageSchema>()

    override fun toEntries(proto: T): List<ProtoEntry> =
        schemaOf(proto.javaClass).fields.map { f ->
            val raw = runCatching { f.getter.invoke(proto) }.getOrNull()
            when (f.kind) {
                Kind.SCALAR -> ProtoEntry(f.jsonKey, raw?.toString() ?: "", scalarType(f.valueType))
                Kind.ENUM -> ProtoEntry(f.jsonKey, (raw as? Enum<*>)?.name ?: "", "Enum")
                Kind.MESSAGE -> ProtoEntry(f.jsonKey, gson.toJson(jsonValueOf(raw)), "Message")
                Kind.REPEATED -> ProtoEntry(
                    f.jsonKey, gson.toJson(jsonValueOf(raw)), "List",
                    template = gson.toJson(templateFor(f.valueType))
                )
                Kind.MAP -> ProtoEntry(f.jsonKey, gson.toJson(jsonValueOf(raw)), "Map")
            }
        }

    /** Recursively converts a runtime field value into a Gson-serialisable tree. */
    private fun jsonValueOf(v: Any?): Any? = when (v) {
        null -> null
        // JSON can't hold NaN or Infinity as numbers, so send them as text
        // ("NaN", "Infinity", "-Infinity"). Saving turns the text back into the number.
        is Double -> if (v.isFinite()) v else v.toString()
        is Float -> if (v.isFinite()) v else v.toString()
        is String, is Number, is Boolean -> v
        is List<*> -> v.map { jsonValueOf(it) }
        is Map<*, *> -> v.entries.associate { (k, value) -> k.toString() to jsonValueOf(value) }
        else -> when {
            v.javaClass.isEnum -> (v as Enum<*>).name
            isProtoMessage(v.javaClass) -> messageToMap(v)
            else -> v.toString()
        }
    }

    /**
     * Unset `oneof` members are emitted as `null` rather than their default value: [buildMessage]
     * skips nulls, so re-submitting this JSON cannot switch the oneof to another member.
     */
    private fun messageToMap(message: Any): Map<String, Any?> {
        val schema = schemaOf(message.javaClass)
        val unset = schema.oneofs.flatMapTo(HashSet()) { it.unsetMembers(message) }
        val out = LinkedHashMap<String, Any?>()
        for (f in schema.fields) {
            out[f.jsonKey] =
                if (f in unset) null
                else jsonValueOf(runCatching { f.getter.invoke(message) }.getOrNull())
        }
        return out
    }

    override fun updateField(proto: T, key: String, value: String): T {
        val f = schemaOf(proto.javaClass).byKey[key] ?: err("unknown field '$key'")
        val builder = proto.javaClass.getMethod("toBuilder").invoke(proto)
        val parsed: Any? = when (f.kind) {
            Kind.MESSAGE, Kind.REPEATED, Kind.MAP -> parseJson(value)
            else -> value // scalar/enum arrive as a plain string
        }
        applyField(builder, f, parsed)
        return proto.javaClass.cast(builder.javaClass.getMethod("build").invoke(builder))
    }

    /** Builds a fresh message of [clazz] from a parsed JSON object. */
    private fun buildMessage(clazz: Class<*>, node: Map<String, Any?>): Any {
        val schema = schemaOf(clazz)
        for (oneof in schema.oneofs) {
            val set = oneof.members.filter { node[it.jsonKey] != null }
            if (set.size > 1) {
                err("only one of ${set.joinToString { "'${it.jsonKey}'" }} can be set (oneof '${oneof.name}')")
            }
        }
        val builder = clazz.getMethod("newBuilder").invoke(null)
        for ((k, v) in node) {
            if (v == null) continue
            val f = schema.byKey[k] ?: err("unknown field '$k' for ${clazz.simpleName}")
            applyField(builder, f, v)
        }
        return builder.javaClass.getMethod("build").invoke(builder)
    }

    /**
     * Applies a value to a builder. [value] is a plain string for scalar/enum fields, a parsed JSON
     * object for messages and maps, or a parsed JSON array for repeated fields.
     */
    private fun applyField(builder: Any, f: FieldSchema, value: Any?) {
        val base = f.accessorName
        when (f.kind) {
            Kind.SCALAR, Kind.ENUM ->
                singleArgMethod(builder, "set$base", f.valueType).call(builder, coerceScalar(f.valueType, value))

            Kind.MESSAGE -> {
                if (value == null) return
                singleArgMethod(builder, "set$base", f.valueType).call(builder, buildMessage(f.valueType, asObject(value)))
            }

            Kind.REPEATED -> {
                builderMethod(builder, "clear$base") { it.parameterCount == 0 }.call(builder)
                for (item in asArray(value)) {
                    val arg = if (isProtoMessage(f.valueType)) buildMessage(f.valueType, asObject(item))
                    else coerceScalar(f.valueType, item)
                    singleArgMethod(builder, "add$base", f.valueType).call(builder, arg)
                }
            }

            Kind.MAP -> {
                val keyType = f.keyType ?: err("map field '${f.jsonKey}' cannot be written")
                val put = builderMethod(builder, "put$base") {
                    it.parameterCount == 2 &&
                        it.parameterTypes[0] == keyType && it.parameterTypes[1] == f.valueType
                }
                builderMethod(builder, "clear$base") { it.parameterCount == 0 }.call(builder)
                // JSON object keys are always strings; coerceScalar parses them to the key type.
                for ((k, v) in asObject(value)) {
                    val arg = if (isProtoMessage(f.valueType)) buildMessage(f.valueType, asObject(v))
                    else coerceScalar(f.valueType, v)
                    put.call(builder, coerceScalar(keyType, k), arg)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Schema discovery
    // ---------------------------------------------------------------------------------------------

    private fun schemaOf(clazz: Class<*>): MessageSchema = schemaCache.getOrPut(clazz) {
        val methods = clazz.methods
        val names = methods.mapTo(HashSet()) { it.name }
        // No-arg instance getters by name, to check what a companion getter returns.
        val getters = methods
            .filter { !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }
            .associateBy { it.name }
        val fields = ArrayList<FieldSchema>()
        val caseGetters = ArrayList<Method>()
        // Generated helpers are recognised by a companion method or by their return type, never
        // by name suffix alone: a field may itself be called `retry_count`, `site_map`, ...
        for (m in getters.values) {
            val n = m.name
            if (!n.startsWith("get") || n in IGNORED_GETTERS) continue
            val rt = m.returnType

            // map field: getXMap() : Map, paired with getXCount(). The deprecated getX() : Map
            // has no getXCount() partner when X itself ends in "Map", so it is skipped here.
            if (n.endsWith("Map") && Map::class.java.isAssignableFrom(rt)) {
                val base = n.substring(3).removeSuffix("Map")
                if ("get${base}Count" in names) {
                    // getXOrDefault(K, V) carries the key and value types; without it the map is
                    // still shown but cannot be written.
                    val types = methods.firstOrNull {
                        it.name == "get${base}OrDefault" && it.parameterCount == 2
                    }?.parameterTypes
                    fields += FieldSchema(
                        base, camelToSnake(base), Kind.MAP, m,
                        valueType = types?.get(1) ?: Any::class.java,
                        keyType = types?.get(0),
                    )
                }
                continue
            }

            // repeated field: getXList() : List, paired with getXCount()
            if (n.endsWith("List") && List::class.java.isAssignableFrom(rt)) {
                val base = n.substring(3).removeSuffix("List")
                if ("get${base}Count" !in names) continue
                // repeated enums also generate getXValueList() : List<Integer>; keep the typed one
                if (base.endsWith("Value") &&
                    elementType(methods, "get${base.removeSuffix("Value")}")?.isEnum == true
                ) continue
                val elem = elementType(methods, "get$base") ?: continue
                fields += FieldSchema(base, camelToSnake(base), Kind.REPEATED, m, elem)
                continue
            }

            if (n.endsWith("OrBuilder") && rt.isInterface) continue
            // repeated and map fields generate getXCount() alongside getXList() / getXMap()
            if (n.endsWith("Count")) {
                val base = n.removeSuffix("Count")
                if ("${base}List" in names || "${base}Map" in names) continue
            }
            // enums generate getXValue() : int alongside getX() : EnumType — keep the typed one
            if (n.endsWith("Value") && getters[n.removeSuffix("Value")]?.returnType?.isEnum == true) continue
            // oneof case selectors: getXCase() returns the generated enum named XCase
            if (n.endsWith("Case") && rt.isEnum && rt.simpleName == n.substring(3)) {
                caseGetters += m
                continue
            }
            // includes the deprecated getX() : Map of a map field
            if (Map::class.java.isAssignableFrom(rt) || List::class.java.isAssignableFrom(rt)) continue
            // string fields generate getXBytes() : ByteString
            if (isByteString(rt)) continue
            if (rt.simpleName.endsWith("Builder") && !isProtoMessage(rt)) continue

            val base = n.substring(3)
            val kind = when {
                rt.isEnum -> Kind.ENUM
                isProtoMessage(rt) -> Kind.MESSAGE
                else -> Kind.SCALAR
            }
            fields += FieldSchema(base, camelToSnake(base), kind, m, rt)
        }
        MessageSchema(fields, oneofsOf(caseGetters, fields))
    }

    /**
     * Groups oneof members by their case selector. The case enum has one constant per member,
     * named after the field (`NOTE`, `A_B_C`), plus `X_NOT_SET`.
     */
    private fun oneofsOf(caseGetters: List<Method>, fields: List<FieldSchema>): List<OneofSchema> =
        caseGetters.map { getter ->
            val memberNames = getter.returnType.enumConstants.orEmpty()
                .map { normalize((it as Enum<*>).name) }
                .toSet()
            OneofSchema(
                name = camelToSnake(getter.name.removePrefix("get").removeSuffix("Case")),
                caseGetter = getter,
                members = fields.filter { normalize(it.jsonKey) in memberNames },
            )
        }

    private fun elementType(methods: Array<Method>, indexedGetter: String): Class<*>? =
        methods.firstOrNull {
            it.name == indexedGetter && it.parameterCount == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType
        }?.returnType

    /** Duck-types a protobuf message: it always exposes a static `newBuilder()` and `toBuilder()`. */
    private fun isProtoMessage(clazz: Class<*>): Boolean =
        !clazz.isEnum && !clazz.isPrimitive && clazz != String::class.java &&
            runCatching { clazz.getMethod("newBuilder") }.isSuccess &&
            runCatching { clazz.getMethod("toBuilder") }.isSuccess

    private fun isByteString(clazz: Class<*>): Boolean = clazz.name == "com.google.protobuf.ByteString"

    // ---------------------------------------------------------------------------------------------
    // Coercion & helpers
    // ---------------------------------------------------------------------------------------------

    private fun coerceScalar(target: Class<*>, v: Any?): Any {
        if (target.isEnum) return enumValue(target, v)
        return when (target) {
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> when (v) {
                is Boolean -> v
                is String -> v.toBooleanStrictOrNull() ?: err("\"$v\" is not a valid Boolean")
                else -> err("\"$v\" is not a valid Boolean")
            }
            Int::class.javaPrimitiveType, Int::class.javaObjectType ->
                asLong(v).let {
                    if (it < Int.MIN_VALUE || it > Int.MAX_VALUE) err("\"$v\" is out of Int range") else it.toInt()
                }
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> asLong(v)
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> asDouble(v).toFloat()
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> asDouble(v)
            String::class.java -> v?.toString() ?: ""
            else -> err("unsupported field type ${target.simpleName}")
        }
    }

    private fun asLong(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong() ?: err("\"$v\" is not a valid integer")
        else -> err("\"$v\" is not a valid number")
    }

    private fun asDouble(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: err("\"$v\" is not a valid number")
        else -> err("\"$v\" is not a valid number")
    }

    private fun enumValue(target: Class<*>, v: Any?): Any {
        val name = v?.toString() ?: err("missing enum value")
        // UNRECOGNIZED stands for unknown wire values; protobuf refuses to set it.
        val settable = target.enumConstants.orEmpty().filter { (it as Enum<*>).name != UNRECOGNIZED }
        return settable.firstOrNull { (it as Enum<*>).name == name }
            ?: err("\"$name\" is not a valid ${target.simpleName} (one of: ${settable.joinToString { (it as Enum<*>).name }})")
    }

    private fun templateFor(elem: Class<*>): Any? = when {
        isProtoMessage(elem) -> messageToMap(elem.getMethod("getDefaultInstance").invoke(null))
        elem.isEnum -> (elem.enumConstants?.firstOrNull() as? Enum<*>)?.name ?: ""
        else -> when (scalarType(elem)) {
            "Boolean" -> false
            "Int", "Long" -> 0
            "Float", "Double" -> 0.0
            else -> ""
        }
    }

    private fun scalarType(t: Class<*>): String = when (t) {
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> "Boolean"
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> "Int"
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> "Long"
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> "Float"
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> "Double"
        else -> "String"
    }

    private fun parseJson(value: String): Any? = try {
        gson.fromJson(value, Any::class.java)
    } catch (e: JsonSyntaxException) {
        err("invalid JSON: ${e.message}")
    }

    /**
     * Finds `setX(T)` / `addX(T)` by exact parameter type, which skips the generated
     * `setX(T.Builder)` overload without relying on type names.
     */
    private fun singleArgMethod(builder: Any, name: String, paramType: Class<*>): Method =
        builderMethod(builder, name) { it.parameterCount == 1 && it.parameterTypes[0] == paramType }

    private fun builderMethod(builder: Any, name: String, predicate: (Method) -> Boolean): Method =
        builder.javaClass.methods.firstOrNull { it.name == name && predicate(it) }
            ?: err("no settable method '$name' on ${builder.javaClass.simpleName}")

    /**
     * Display key for a generated accessor name. protoc upper-cases the letter after each
     * underscore, so every capital after the first marks a word boundary (`ABC` -> `a_b_c`).
     */
    private fun camelToSnake(name: String): String =
        name.replace(Regex("(?<!^)(?=[A-Z])"), "_").lowercase()

    @Suppress("UNCHECKED_CAST")
    private fun asObject(v: Any?): Map<String, Any?> =
        v as? Map<String, Any?> ?: err("expected a JSON object but got ${v?.javaClass?.simpleName ?: "null"}")

    @Suppress("UNCHECKED_CAST")
    private fun asArray(v: Any?): List<Any?> =
        v as? List<Any?> ?: err("expected a JSON array but got ${v?.javaClass?.simpleName ?: "null"}")

    private fun err(message: String): Nothing = throw IllegalArgumentException(message)

    /** Invokes reflectively, unwrapping the underlying failure into an [IllegalArgumentException]. */
    private fun Method.call(receiver: Any?, vararg args: Any?): Any? = try {
        invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw IllegalArgumentException(e.targetException.message ?: e.message ?: "update failed")
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: ReflectiveOperationException) {
        throw IllegalArgumentException(e.message ?: "update failed")
    }

    private companion object {
        private val gson = Gson()
        private const val UNRECOGNIZED = "UNRECOGNIZED"

        /** Compares a JSON key with a oneof case constant name: `a_b_c` and `A_B_C` both -> `abc`. */
        private fun normalize(name: String): String = name.replace("_", "").lowercase()

        private val IGNORED_GETTERS = setOf(
            "getClass", "getDefaultInstance", "getDefaultInstanceForType", "getSerializedSize",
            "getAllFields", "getUnknownFields", "getParserForType", "getDescriptorForType",
            "getInitializationErrorString", "getMemoizedSerializedSize",
        )
    }
}
