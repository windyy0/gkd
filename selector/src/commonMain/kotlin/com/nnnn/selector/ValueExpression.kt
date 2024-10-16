package com.nnnn.selector

import kotlin.js.JsExport

@JsExport
sealed class ValueExpression(open val value: Any?, open val type: String) : Position {
    override fun stringify() = value.toString()
    internal abstract fun <T> getAttr(
        context: Context<T>,
        transform: Transform<T>,
    ): Any?

    abstract val properties: Array<String>
    abstract val methods: Array<String>

    sealed class Variable(
        override val value: String,
    ) : ValueExpression(value, "var")

    data class Identifier(
        override val start: Int,
        val name: String,
    ) : Variable(name) {
        override val end = start + value.length
        override fun <T> getAttr(context: Context<T>, transform: Transform<T>): Any? {
            return transform.getAttr(context, value)
        }

        override val properties: Array<String>
            get() = arrayOf(value)
        override val methods: Array<String>
            get() = emptyArray()

        val isEqual = name == "equal"
        val isNotEqual = name == "notEqual"
    }

    data class MemberExpression(
        override val start: Int,
        override val end: Int,
        val object0: Variable,
        val property: String,
    ) : Variable(value = "${object0.stringify()}.$property") {
        override fun <T> getAttr(
            context: Context<T>,
            transform: Transform<T>,
        ): Any? {
            return transform.getAttr(
                object0.getAttr(context, transform).whenNull { return null },
                property
            )
        }

        override val properties: Array<String>
            get() = arrayOf(*object0.properties, property)
        override val methods: Array<String>
            get() = object0.methods

        val isPropertyOr = property == "or"
        val isPropertyAnd = property == "and"
        val isPropertyIfElse = property == "ifElse"
    }

    data class CallExpression(
        override val start: Int,
        override val end: Int,
        val callee: Variable,
        val arguments: List<ValueExpression>,
    ) : Variable(
        value = "${callee.stringify()}(${arguments.joinToString(",") { it.stringify() }})",
    ) {

        override fun <T> getAttr(
            context: Context<T>,
            transform: Transform<T>,
        ): Any? {
            return when (callee) {
                is CallExpression -> {
                    // not support
                    null
                }

                is Identifier -> {
                    when {
                        callee.isEqual -> {
                            CompareOperator.Equal.compare(
                                arguments[0].getAttr(context, transform),
                                arguments[1].getAttr(context, transform)
                            )
                        }

                        callee.isNotEqual -> {
                            !CompareOperator.Equal.compare(
                                arguments[0].getAttr(context, transform),
                                arguments[1].getAttr(context, transform)
                            )
                        }

                        else -> {
                            transform.getInvoke(
                                context,
                                callee.name,
                                arguments.map {
                                    it.getAttr(context, transform).whenNull { return null }
                                }
                            )
                        }
                    }
                }

                is MemberExpression -> {
                    val objectValue =
                        callee.object0.getAttr(context, transform).whenNull { return null }
                    when {
                        callee.isPropertyOr -> {
                            (objectValue as Boolean) ||
                                    (arguments[0].getAttr(context, transform)
                                        .whenNull { return null } as Boolean)
                        }

                        callee.isPropertyAnd -> {
                            (objectValue as Boolean) &&
                                    (arguments[0].getAttr(context, transform)
                                        .whenNull { return null } as Boolean)
                        }

                        callee.isPropertyIfElse -> {
                            if (objectValue as Boolean) {
                                arguments[0].getAttr(context, transform)
                            } else {
                                arguments[1].getAttr(context, transform)
                            }
                        }

                        else -> transform.getInvoke(
                            objectValue,
                            callee.property,
                            arguments.map {
                                it.getAttr(context, transform).whenNull { return null }
                            }
                        )
                    }

                }
            }
        }

        override val properties: Array<String>
            get() = callee.properties.toMutableList()
                .plus(arguments.flatMap { it.properties.toList() })
                .toTypedArray()
        override val methods: Array<String>
            get() = when (callee) {
                is CallExpression -> callee.methods
                is Identifier -> arrayOf(callee.name)
                is MemberExpression -> arrayOf(*callee.object0.methods, callee.property)
            }.toMutableList().plus(arguments.flatMap { it.methods.toList() })
                .toTypedArray()
    }

    sealed class LiteralExpression(
        override val value: Any?,
        override val type: String,
    ) : ValueExpression(value, type) {
        override fun <T> getAttr(
            context: Context<T>,
            transform: Transform<T>,
        ) = value

        override val properties: Array<String>
            get() = emptyArray()
        override val methods: Array<String>
            get() = emptyArray()
    }

    data class NullLiteral(
        override val start: Int,
    ) : LiteralExpression(null, "null") {
        override val end = start + 4
    }

    data class BooleanLiteral(
        override val start: Int,
        override val value: Boolean
    ) : LiteralExpression(value, "boolean") {
        override val end = start + if (value) 4 else 5
    }

    data class IntLiteral(
        override val start: Int,
        override val end: Int,
        override val value: Int
    ) : LiteralExpression(value, "int")

    data class StringLiteral @JsExport.Ignore constructor(
        override val start: Int,
        override val end: Int,
        override val value: String,
        internal val matches: ((CharSequence) -> Boolean)? = null
    ) : LiteralExpression(value, "string") {

        override fun stringify() = escapeString(value)

        internal val outMatches = matches?.let { optimizeMatchString(value) ?: it } ?: { false }
    }
}
