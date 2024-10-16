package com.nnnn.selector

import kotlin.js.JsExport

fun escapeString(value: String, wrapChar: Char = '"'): String {
    val sb = StringBuilder(value.length + 2)
    sb.append(wrapChar)
    value.forEach { c ->
        val escapeChar = when (c) {
            wrapChar -> wrapChar
            '\n' -> 'n'
            '\r' -> 'r'
            '\t' -> 't'
            '\b' -> 'b'
            '\\' -> '\\'
            else -> null
        }
        if (escapeChar != null) {
            sb.append("\\" + escapeChar)
        } else {
            when (c.code) {
                in 0..0xf -> {
                    sb.append("\\x0" + c.code.toString(16))
                }

                in 0x10..0x1f -> {
                    sb.append("\\x" + c.code.toString(16))
                }

                else -> {
                    sb.append(c)
                }
            }
        }
    }
    sb.append(wrapChar)
    return sb.toString()
}

private const val REG_SPECIAL_STRING = "\\^$.?*|+()[]{}"
private fun getMatchValue(value: String, prefix: String, suffix: String): String? {
    if (value.startsWith(prefix) && value.endsWith(suffix) && value.length >= (prefix.length + suffix.length)) {
        for (i in prefix.length until value.length - suffix.length) {
            if (value[i] in REG_SPECIAL_STRING) {
                return null
            }
        }
        return value.subSequence(prefix.length, value.length - suffix.length).toString()
    }
    return null
}

internal fun optimizeMatchString(value: String): ((CharSequence) -> Boolean)? {
    getMatchValue(value, "(?is)", ".*")?.let { startsWithValue ->
        return { value -> value.startsWith(startsWithValue, ignoreCase = true) }
    }
    getMatchValue(value, "(?is).*", ".*")?.let { containsValue ->
        return { value -> value.contains(containsValue, ignoreCase = true) }
    }
    getMatchValue(value, "(?is).*", "")?.let { endsWithValue ->
        return { value -> value.endsWith(endsWithValue, ignoreCase = true) }
    }
    return null
}

internal inline fun <T> T?.whenNull(block: () -> Nothing): T {
    if (this == null) {
        block()
    }
    return this
}

@JsExport
class DefaultTypeInfo(
    val booleanType: _root_ide_package_.com.nnnn.selector.TypeInfo,
    val intType: _root_ide_package_.com.nnnn.selector.TypeInfo,
    val stringType: _root_ide_package_.com.nnnn.selector.TypeInfo,
    val contextType: _root_ide_package_.com.nnnn.selector.TypeInfo,
    val nodeType: _root_ide_package_.com.nnnn.selector.TypeInfo,
    val globalType: _root_ide_package_.com.nnnn.selector.TypeInfo
)

@JsExport
fun initDefaultTypeInfo(webField: Boolean = false): DefaultTypeInfo {
    val booleanType =
        _root_ide_package_.com.nnnn.selector.TypeInfo(_root_ide_package_.com.nnnn.selector.PrimitiveType.BooleanType)
    val intType =
        _root_ide_package_.com.nnnn.selector.TypeInfo(_root_ide_package_.com.nnnn.selector.PrimitiveType.IntType)
    val stringType =
        _root_ide_package_.com.nnnn.selector.TypeInfo(_root_ide_package_.com.nnnn.selector.PrimitiveType.StringType)
    val nodeType = _root_ide_package_.com.nnnn.selector.TypeInfo(
        _root_ide_package_.com.nnnn.selector.PrimitiveType.ObjectType("node")
    )
    val contextType = _root_ide_package_.com.nnnn.selector.TypeInfo(
        _root_ide_package_.com.nnnn.selector.PrimitiveType.ObjectType("context")
    )
    val globalType = _root_ide_package_.com.nnnn.selector.TypeInfo(
        _root_ide_package_.com.nnnn.selector.PrimitiveType.ObjectType("global")
    )

    fun buildMethods(name: String, returnType: _root_ide_package_.com.nnnn.selector.TypeInfo, paramsSize: Int): Array<_root_ide_package_.com.nnnn.selector.MethodInfo> {
        return arrayOf(
            _root_ide_package_.com.nnnn.selector.MethodInfo(
                name,
                returnType,
                Array(paramsSize) { booleanType }),
            _root_ide_package_.com.nnnn.selector.MethodInfo(
                name,
                returnType,
                Array(paramsSize) { intType }),
            _root_ide_package_.com.nnnn.selector.MethodInfo(
                name,
                returnType,
                Array(paramsSize) { stringType }),
            _root_ide_package_.com.nnnn.selector.MethodInfo(
                name,
                returnType,
                Array(paramsSize) { nodeType }),
            _root_ide_package_.com.nnnn.selector.MethodInfo(
                name,
                returnType,
                Array(paramsSize) { contextType }),
        )
    }

    booleanType.methods = arrayOf(
        _root_ide_package_.com.nnnn.selector.MethodInfo("toInt", intType),
        _root_ide_package_.com.nnnn.selector.MethodInfo("or", booleanType, arrayOf(booleanType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("and", booleanType, arrayOf(booleanType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("not", booleanType),
        *buildMethods("ifElse", booleanType, 2),
    )

    intType.methods = arrayOf(
        _root_ide_package_.com.nnnn.selector.MethodInfo("toString", stringType),
        _root_ide_package_.com.nnnn.selector.MethodInfo("toString", stringType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("plus", intType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("minus", intType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("times", intType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("div", intType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("rem", intType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("more", booleanType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("moreEqual", booleanType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("less", booleanType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("lessEqual", booleanType, arrayOf(intType)),
    )
    stringType.props = arrayOf(
        _root_ide_package_.com.nnnn.selector.PropInfo("length", intType),
    )
    stringType.methods = arrayOf(
        _root_ide_package_.com.nnnn.selector.MethodInfo("get", stringType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("at", stringType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("substring", stringType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo(
            "substring",
            stringType,
            arrayOf(intType, intType)
        ),
        _root_ide_package_.com.nnnn.selector.MethodInfo("toInt", intType),
        _root_ide_package_.com.nnnn.selector.MethodInfo("toInt", intType, arrayOf(intType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo("indexOf", intType, arrayOf(stringType)),
        _root_ide_package_.com.nnnn.selector.MethodInfo(
            "indexOf",
            intType,
            arrayOf(stringType, intType)
        ),
    )
    nodeType.props = arrayOf(
        * (if (webField) {
            arrayOf(
                _root_ide_package_.com.nnnn.selector.PropInfo("_id", intType),
                _root_ide_package_.com.nnnn.selector.PropInfo("_pid", intType),
            )
        } else {
            emptyArray()
        }),

        _root_ide_package_.com.nnnn.selector.PropInfo("id", stringType),
        _root_ide_package_.com.nnnn.selector.PropInfo("vid", stringType),
        _root_ide_package_.com.nnnn.selector.PropInfo("name", stringType),
        _root_ide_package_.com.nnnn.selector.PropInfo("text", stringType),
        _root_ide_package_.com.nnnn.selector.PropInfo("desc", stringType),

        _root_ide_package_.com.nnnn.selector.PropInfo("clickable", booleanType),
        _root_ide_package_.com.nnnn.selector.PropInfo("focusable", booleanType),
        _root_ide_package_.com.nnnn.selector.PropInfo("checkable", booleanType),
        _root_ide_package_.com.nnnn.selector.PropInfo("checked", booleanType),
        _root_ide_package_.com.nnnn.selector.PropInfo("editable", booleanType),
        _root_ide_package_.com.nnnn.selector.PropInfo("longClickable", booleanType),
        _root_ide_package_.com.nnnn.selector.PropInfo("visibleToUser", booleanType),

        _root_ide_package_.com.nnnn.selector.PropInfo("left", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("top", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("right", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("bottom", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("width", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("height", intType),

        _root_ide_package_.com.nnnn.selector.PropInfo("childCount", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("index", intType),
        _root_ide_package_.com.nnnn.selector.PropInfo("depth", intType),

        _root_ide_package_.com.nnnn.selector.PropInfo("parent", nodeType),
    )
    nodeType.methods = arrayOf(
        _root_ide_package_.com.nnnn.selector.MethodInfo("getChild", nodeType, arrayOf(intType)),
    )
    contextType.methods = arrayOf(
        *nodeType.methods,
        _root_ide_package_.com.nnnn.selector.MethodInfo("getPrev", contextType, arrayOf(intType))
    )
    contextType.props = arrayOf(
        *nodeType.props,
        _root_ide_package_.com.nnnn.selector.PropInfo("prev", contextType),
        _root_ide_package_.com.nnnn.selector.PropInfo("current", nodeType),
    )
    globalType.methods = arrayOf(
        *contextType.methods,
        *buildMethods("equal", booleanType, 2),
        *buildMethods("notEqual", booleanType, 2),
    )
    globalType.props = arrayOf(*contextType.props)
    return DefaultTypeInfo(
        booleanType = booleanType,
        intType = intType,
        stringType = stringType,
        contextType = contextType,
        nodeType = nodeType,
        globalType = globalType
    )
}

@JsExport
fun getIntInvoke(target: Int, name: String, args: List<Any>): Any? {
    return when (name) {
        "plus" -> {
            target + args.getInt()
        }

        "minus" -> {
            target - args.getInt()
        }

        "times" -> {
            target * args.getInt()
        }

        "div" -> {
            target / args.getInt().also { if (it == 0) return null }
        }

        "rem" -> {
            target % args.getInt().also { if (it == 0) return null }
        }

        "more" -> {
            target > args.getInt()
        }

        "moreEqual" -> {
            target >= args.getInt()
        }

        "less" -> {
            target < args.getInt()
        }

        "lessEqual" -> {
            target <= args.getInt()
        }

        else -> null
    }
}


internal fun List<Any>.getInt(i: Int = 0) = get(i) as Int

@JsExport
fun getStringInvoke(target: String, name: String, args: List<Any>): Any? {
    return getCharSequenceInvoke(target, name, args)
}

@JsExport
fun getBooleanInvoke(target: Boolean, name: String, args: List<Any>): Any? {
    return when (name) {
        "toInt" -> if (target) 1 else 0
        "not" -> !target
        else -> null
    }
}

fun getCharSequenceInvoke(target: CharSequence, name: String, args: List<Any>): Any? {
    return when (name) {
        "get" -> {
            target.getOrNull(args.getInt()).toString()
        }

        "at" -> {
            val i = args.getInt()
            if (i < 0) {
                target.getOrNull(target.length + i).toString()
            } else {
                target.getOrNull(i).toString()
            }
        }

        "substring" -> {
            when (args.size) {
                1 -> {
                    val start = args.getInt()
                    if (start < 0) return null
                    if (start >= target.length) return ""
                    target.substring(
                        start,
                        target.length
                    )
                }

                2 -> {
                    val start = args.getInt()
                    if (start < 0) return null
                    if (start >= target.length) return ""
                    val end = args.getInt(1)
                    if (end < start) return null
                    target.substring(
                        start,
                        end.coerceAtMost(target.length)
                    )
                }

                else -> {
                    null
                }
            }
        }

        "toInt" -> when (args.size) {
            0 -> target.toString().toIntOrNull()
            1 -> {
                val radix = args.getInt()
                if (radix !in 2..36) {
                    return null
                }
                target.toString().toIntOrNull(radix)
            }

            else -> null
        }

        "indexOf" -> {
            when (args.size) {
                1 -> {
                    val str = args[0] as? CharSequence ?: return null
                    target.indexOf(str.toString())
                }

                2 -> {
                    val str = args[0] as? CharSequence ?: return null
                    val startIndex = args.getInt(1)
                    target.indexOf(str.toString(), startIndex)
                }

                else -> null
            }
        }

        else -> null
    }
}

@JsExport
fun getStringAttr(target: String, name: String): Any? {
    return getCharSequenceAttr(target, name)
}

fun getCharSequenceAttr(target: CharSequence, name: String): Any? {
    return when (name) {
        "length" -> target.length
        else -> null
    }
}
