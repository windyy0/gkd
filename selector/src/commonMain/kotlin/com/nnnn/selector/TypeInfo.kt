package com.nnnn.selector

import kotlin.js.JsExport

@JsExport
sealed class PrimitiveType(val key: String) {
    data object BooleanType : com.nnnn.selector.PrimitiveType("boolean")
    data object IntType : com.nnnn.selector.PrimitiveType("int")
    data object StringType : com.nnnn.selector.PrimitiveType("string")
    data class ObjectType(val name: String) : com.nnnn.selector.PrimitiveType("object")
}

@JsExport
data class MethodInfo(
    val name: String,
    val returnType: com.nnnn.selector.TypeInfo,
    val params: Array<com.nnnn.selector.TypeInfo> = emptyArray(),
) : com.nnnn.selector.Stringify {
    override fun stringify(): String {
        return "$name(${params.joinToString(", ") { it.stringify() }}): ${returnType.stringify()}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as com.nnnn.selector.MethodInfo

        if (name != other.name) return false
        if (returnType != other.returnType) return false
        if (!params.contentEquals(other.params)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + params.contentHashCode()
        return result
    }
}

@JsExport
data class PropInfo(
    val name: String,
    val type: com.nnnn.selector.TypeInfo,
)

@JsExport
data class TypeInfo(
    val type: com.nnnn.selector.PrimitiveType,
    var props: Array<com.nnnn.selector.PropInfo> = arrayOf(),
    var methods: Array<com.nnnn.selector.MethodInfo> = arrayOf(),
) : com.nnnn.selector.Stringify {
    override fun stringify(): String {
        return if (type is com.nnnn.selector.PrimitiveType.ObjectType) {
            type.name
        } else {
            type.key
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as _root_ide_package_.com.nnnn.selector.TypeInfo

        if (type != other.type) return false
        if (!props.contentEquals(other.props)) return false
        if (!methods.contentEquals(other.methods)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + props.contentHashCode()
        result = 31 * result + methods.contentHashCode()
        return result
    }
}