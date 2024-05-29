@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.N.Companion.isArray
import com.here.naksha.lib.base.N.Companion.isDataView
import com.here.naksha.lib.base.N.Companion.isDouble
import com.here.naksha.lib.base.N.Companion.isInt64
import com.here.naksha.lib.base.N.Companion.isObject
import com.here.naksha.lib.base.N.Companion.isString
import com.here.naksha.lib.base.N.Companion.newArray
import com.here.naksha.lib.base.N.Companion.newObject
import com.here.naksha.lib.base.N.Companion.toDouble
import com.here.naksha.lib.base.N.Companion.toInt64
import com.here.naksha.lib.base.N.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Klass is a basic reflection type needed for the multi-platform base library. There are pre-defined types for the
 * supported base multi-platform types, no other platform types should exist.
 * @param <T> The platform type.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
abstract class Klass<out T> {
    companion object {
        /**
         * The Klass for Any/Object.
         */
        @JvmStatic
        var anyKlass = object : Klass<Any>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = true

            override fun newInstance(vararg args: Any?): Any = throw UnsupportedOperationException("Any can't be instantiated")
        }

        /**
         * The Klass for Boolean.
         */
        @JvmStatic
        val boolKlass = object : Klass<Boolean>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is Boolean

            override fun newInstance(vararg args: Any?): Boolean {
                if (args.isEmpty()) return false
                return when (val a = args[0]) {
                    is Boolean -> a
                    is String -> a.isNotEmpty() && a.lowercase() != "false"
                    is Byte, Short, Int -> a != 0
                    is Int64 -> !N.eqi(a, 0)
                    is Float, Double -> a != 0.0
                    else -> false
                }
            }
        }

        /**
         * The Klass for Int.
         */
        @JvmStatic
        val intKlass = object : Klass<Int>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is Int

            override fun newInstance(vararg args: Any?): Int {
                if (args.isEmpty()) return 0
                val a = args[0]
                if (a == null || a == undefined) return 0
                return N.toInt(a)
            }
        }

        /**
         * The Klass for Int64.
         */
        @JvmStatic
        var int64Klass = object : Klass<Int64>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = isInt64(o)

            override fun newInstance(vararg args: Any?): Int64 {
                if (args.isEmpty()) return toInt64(0)
                val a = args[0]
                if (a == null || a == undefined) return toInt64(0)
                return toInt64(a)
            }
        }

        /**
         * The Klass for Double.
         */
        @JvmStatic
        var doubleKlass = object : Klass<Double>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = isDouble(o)

            override fun newInstance(vararg args: Any?): Double {
                if (args.isEmpty()) return Double.NaN
                val a = args[0] ?: return Double.NaN
                if (a == undefined) return Double.NaN
                return toDouble(a)
            }
        }

        /**
         * The Klass for String.
         */
        @JvmStatic
        var stringKlass = object : Klass<String>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = isString(o)

            override fun newInstance(vararg args: Any?): String {
                if (args.isEmpty()) return ""
                val a = args[0] ?: return "null"
                if (a == undefined) return "undefined"
                return a.toString()
            }
        }

       /**
         * The Klass for [Symbol].
         */
        @JvmStatic
        var symbolKlass = object : Klass<Symbol>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is Symbol

            override fun newInstance(vararg args: Any?): Symbol {
                if (args.isEmpty()) return N.symbol(null)
                val a = args[0]
                require(a is String) {"Symbols can only be found to strings in the global registry"}
                return N.symbol(a)
            }
        }

        /**
         * The Klass for [N_Array].
         */
        @JvmStatic
        var arrayKlass = object : Klass<N_Array>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = isArray(o)

            override fun newInstance(vararg args: Any?): N_Array = newArray(args)
        }

        /**
         * The Klass for [N_Object].
         */
        @JvmStatic
        var objectKlass = object : Klass<N_Object>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = isObject(o)

            override fun newInstance(vararg args: Any?): N_Object = newObject(args)
        }

        /**
         * The Klass for [N_DataView].
         */
        @JvmStatic
        var dataViewKlass = object : Klass<N_DataView>() {
            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = isDataView(o)

            override fun newInstance(vararg args: Any?): N_DataView {
                require(args.isNotEmpty()) { "To create a view a ByteArray must be given as first argument to the constructor" }
                val byteArray = args[0]
                require(byteArray is ByteArray) { "Invalid first argument, must be ByteArray" }
                return when (args.size) {
                    1 -> N.newDataView(byteArray)
                    2 -> {
                        val offset = args[1]
                        require(offset is Int) { "Invalid second argument, offset must be Int" }
                        N.newDataView(byteArray, offset)
                    }

                    3 -> {
                        val offset = args[1]
                        require(offset is Int) { "Invalid second argument, offset must be Int" }
                        val length = args[2]
                        require(length is Int) { "Invalid third argument, length must be Int" }
                        N.newDataView(byteArray, offset, length)
                    }

                    else -> throw IllegalArgumentException("DataView constructor has maximal 3 arguments: byteArray, offset, length")
                }
            }
        }
    }

    /**
     * Returns _true_ if this is an [N_Array] or [OldBaseArray].
     * @return _true_ if this is an [N_Array] or [OldBaseArray].
     */
    abstract fun isArray(): Boolean

    /**
     * Tests whether the given object is an instance of this type.
     * @param o The object to test.
     * @return _true_ if the given object is a type of this class; _false_ otherwise.
     */
    abstract fun isInstance(o: Any?): Boolean

    /**
     * Creates a new platform instance.
     * @param args The optional arguments for the constructor.
     * @return The new instance.
     * @throws UnsupportedOperationException If no instance can be created.
     */
    abstract fun newInstance(vararg args: Any?): T
}