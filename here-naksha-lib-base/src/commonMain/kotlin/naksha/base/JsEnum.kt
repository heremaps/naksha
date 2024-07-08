@file:Suppress("LeakingThis")

package naksha.base

import naksha.base.fn.Fx1
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * A custom enumeration implementation that supports more flexible enumerations. Creating enumeration values requires to implement
 * [namespace]. The implementation is only done in the class directly extending the [JsEnum], which is called as well the **namespace**.
 *
 * An implementation normally looks like:
 * ```
 * open class Vehicle : JsEnum()
 * {
 *   override fun namespace(): KClass<out JsEnum> = Vehicle::class
 *   open fun type(): String = "Vehicle"
 * }
 * class Car : Vehicle() {
 *   companion object {
 *     @JvmStatic
 *     @JsStatic
 *     val BAR = def(Car::class, "bar")
 *   }
 *   override fun type(): String = "Car"
 * }
 * class Truck : Vehicle() {
 *   companion object {
 *     @JvmStatic
 *     @JsStatic
 *     val FOO = def(Truck::class, "foo")
 *   }
 *   override fun type(): String = "Truck"
 * }
 * ```
 * Ones created like in the above example, the constants can be used like:
 * ```
 * val bar = JsEnum.get("bar", Vehicle::class)
 * val foo = JsEnum.get("foo", Vehicle::class)
 * val unknown = JsEnum.get("unknown", Vehicle::class)
 * println("$bar is ${bar.type()}")
 * println("$foo is ${foo.type()}")
 * println("$unknown is ${unknown.type()}")
 * ```
 * This should print "bar is Car", "foo is Truck" and "unknown is Vehicle".
 *
 * @constructor Should not be called, please us [def] or [defIgnoreCase].
 */
@Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")
@JsExport
abstract class JsEnum : CharSequence {
    init {
        check(false) { "Do not directly invoke enumeration constructor, please always use def or defIgnoreCase" }
    }

    /**
     * The value that represents NULL in the internal registry.
     */
    private object NULL

    /**
     * The value, either [String], [Int], [Int64], [Double], [Boolean] or _null_.
     */
    var value: Any? = null
        private set

    /**
     * Cached string representation to [toString]. When first called, [createString] is invoked to generate it.
     */
    private var string: String? = null

    /**
     * If the enumeration value is predefined or was generated on-the-fly. This can be used to reject custom
     * enumeration values.
     */
    var isDefined: Boolean = false
        private set

    companion object {
        private fun alignValue(value: Any?): Any? {
            if (value == null) return null
            // Note: Byte, Short, Integer will be converted to Long
            //       Float is converted to Double.
            // This simplifies usage and avoids that a number parsed into a short is not found when being pre-defined.
            return when (value) {
                is Byte -> value.toInt()
                is Short -> value.toInt()
                is Int -> value
                is Long -> Int64(value)
                is Int64 -> value
                is String -> value
                is Float -> value.toDouble()
                is Double -> value
                // Unknown number types are simply converted to double
                is Number -> value.toDouble()
                // Anything else becomes a string
                else -> value.toString()
            }
        }

        /**
         * A mapping between the class and the namespace. The namespace is the "root" class, so the class that directly
         * extends [JsEnum].
         */
        private val klassToNamespace = CMap<KClass<out JsEnum>, KClass<out JsEnum>>()

        /**
         * All registered enumeration values of a namespace. The first level is the namespace (the Kotlin class
         * that directly extend [JsEnum]), the second level maps values to registered instances.
         */
        private val registryMain = CMap<KClass<*>, CMap<Any, JsEnum>>()

        /**
         * All registered enumeration aliases. The first level is the namespace (the Kotlin class that directly extend [JsEnum]), the
         * second level maps values to registered instances.
         */
        private val registryAlias = CMap<KClass<*>, CMap<Any, JsEnum>>()

        private fun mainMap(ns: KClass<out JsEnum>): CMap<Any, JsEnum> {
            var mainMap = registryMain[ns]
            if (mainMap == null) {
                mainMap = CMap()
                val existing = registryMain.putIfAbsent(ns, mainMap)
                if (existing != null) mainMap = existing
            }
            return mainMap
        }

        private fun aliasMap(ns: KClass<out JsEnum>): CMap<Any, JsEnum> {
            var aliasMap = registryAlias[ns]
            if (aliasMap == null) {
                aliasMap = CMap()
                val existing = registryAlias.putIfAbsent(ns, aliasMap)
                if (existing != null) aliasMap = existing
            }
            return aliasMap
        }

        /**
         * Defines a new enumeration value that is not case-sensitive. Beware, that the provided value is still used exactly as given when
         * serializing the value.
         *
         * @param enumKlass the enumeration class.
         * @param value     the value.
         * @return the defined instance.
         * @throws IllegalStateException if another class is already registered for the value (there is a conflict).
         */
        @JvmStatic
        @JsStatic
        fun <ENUM : JsEnum> defIgnoreCase(enumKlass: KClass<ENUM>, value: String, init: Fx1<ENUM>? = null): ENUM {
            val e = def(enumKlass, value, init)
            val s = value.lowercase()
            val aliasMap = aliasMap(e.namespace())
            check(aliasMap.putIfAbsent(s, e) == null) {
                "Conflict, there is already an enumeration value for ${e.value} registered: ${aliasMap[s]!!::class.simpleName}"
            }
            return e
        }

        /**
         * Defines a new enumeration value.
         *
         * @param enumKlass the enumeration class.
         * @param value     the value.
         * @return the defined instance.
         * @throws IllegalStateException if another class is already registered for the value (there is a conflict).
         */
        fun <ENUM : JsEnum> def(enumKlass: KClass<ENUM>, value: Any?, init: Fx1<ENUM>? = null): ENUM {
            require(value === null || value is String || value is Number || value is Int64) {
                "Invalid enumeration value, require null, String or Number"
            }
            val e = __get(value, enumKlass, false)
            e.isDefined = true
            val mainMap = mainMap(e.namespace())
            check(mainMap.putIfAbsent(e.value ?: NULL, e) == null) {
                "Conflict, there is already an enumeration value for ${e.value} registered: ${mainMap[e.value]!!::class.simpleName}"
            }
            e.init()
            init?.call(e)
            return e
        }

        /**
         * Returns the enumeration instance for the given value and namespace. If the value is pre-defined, the
         * singleton is returned, otherwise a new instance is created.
         * @param value The value for which to return the enumeration.
         * @param enumKlass The enumeration klass to query.
         * @return The enumeration for the given value.
         */
        @JvmStatic
        @JsStatic
        fun <ENUM : JsEnum> get(value: Any?, enumKlass: KClass<out ENUM>): ENUM = __get(value, enumKlass, true)

        @Suppress("UNCHECKED_CAST")
        private fun <ENUM : JsEnum> __get(value: Any?, enumKlass: KClass<out ENUM>, doInit: Boolean): ENUM {
            var ns = klassToNamespace[enumKlass]
            if (ns == null) {
                // The class is not yet initialized, allocate an instance.
                // Allocating an instance, should cause the companion object to be initialized.
                val instance = Platform.allocateInstance(enumKlass)
                ns = instance.namespace()
                val existing = klassToNamespace.putIfAbsent(enumKlass, ns)
                check(existing === null || existing === ns) {
                    "There is already another class (${existing!!.simpleName}) registered for namespace ${ns.simpleName}"
                }
                instance.register(ns)
                instance.initClass()
            }
            val mainMap = mainMap(ns)
            val key = alignValue(value) ?: NULL
            var e = mainMap[key] as ENUM?
            if (e == null) {
                val aliasMap = aliasMap(ns)
                e = aliasMap[key] as ENUM?
                if (e == null && key is String) {
                    e = aliasMap[key.lowercase()] as ENUM?
                }
            }
            if (e == null) {
                // The value is not pre-defined, create it on-the-fly.
                e = Platform.allocateInstance(enumKlass)
                e.value = alignValue(value)
                if (doInit) e.init()
            }
            return e
        }
    }

    /**
     * Returns the namespace of this instance. The namespace is the root enumeration type, so the type that
     * directly extends [JsEnum]. Should simply be implemented as:
     * ```
     * val namespace: KClass<out JsEnum> = this::class
     * ```
     * @return The namespace, the Kotlin class that directly extends [JsEnum].
     */
    abstract fun namespace(): KClass<out JsEnum>

    /**
     * Register an enumeration child-class.
     * @param enumKlass the enumeration child-class.
     */
    protected fun <CHILD : JsEnum> register(enumKlass: KClass<out CHILD>) {
        val namespace = namespace()
        val existing = klassToNamespace.putIfAbsent(enumKlass, namespace)
        check(existing === null || existing === namespace) {
            "Failed to register '${enumKlass.simpleName}' to namespace '${namespace.simpleName}'" +
                    ", '${enumKlass.simpleName}' is already registered to '${existing!!.simpleName}'"
        }
        Platform.initializeKlass(enumKlass)
    }


    /**
     * This method is invoked exactly ones per namespace, when the enumeration namespace is not yet initialized. It simplifies
     * auto-initialization. Actually, it is required that the namespace class (the class directly extending the [JsEnum]) implements this
     * method and invokes [register] for all extending (child) classes. For example, when an enumeration class `Vehicle` is created
     * with two extending (child) enumeration classes, being `Car` and `Truck`, then the `initClass` method of the `Vehicle` should do:
     * ```
     * protected fun initClass() {
     *   register(Car::class)
     *   register(Truck::class)
     * }
     * ```
     * This is needed to resolve the chicken-egg problem of the JVM class loading mechanism. The order is not relevant.
     *
     * ## Details
     * There is a chicken-egg problem in the JVM. A class is not loaded before it is needed and even when the class is
     * loaded, it is not directly initialized. In other words, when we create an enumeration class and make constants for
     * possible value, the JVM runtime will not be aware of them unless we force it to load and initialize the class.
     * This means, unless one of the constants are really used, Jackson or other deserialization tools will not be able
     * to deserialize the value. This can lead to serious errors. This initialization method prevents this kind of error.
     */
    protected abstract fun initClass()

    /**
     * This method is invoked if the enumeration value is created via reflection by [get], it should initialize properties
     * to default values, because the constructor is bypassed by the reflection construction. It is invoked after the [value]
     * has been set, so [value] can be read.
     */
    protected open fun init() {}

    /**
     * A method that can be overridden, if the enumeration requires special handling in turning the value into a string.
     */
    protected open fun createString(): String = value?.toString() ?: "null"

    /**
     * Runs a lambda against this enumeration instance. In Kotlin it is better to simply use `.apply {}`.
     *
     * @param selfKlass reference to the class of this enumeration-type.
     * @param lambda the lambda to call with the first parameter being this.
     * @return this.
     */
    @Suppress("UNCHECKED_CAST")
    fun <SELF : JsEnum> with(selfKlass: KClass<SELF>, lambda: Fx1<SELF>): SELF {
        lambda.call(this as SELF)
        return this
    }

    /**
     * Can be used with defined values to add aliases.
     *
     * @param selfClass Reference to the class of this enumeration-type.
     * @param value     The additional value to register.
     * @param <SELF>    The type of this.
     * @return this.
     */
    protected fun <SELF : JsEnum> alias(selfClass: KClass<SELF>, value: Any): SELF {
        val aliasMap = aliasMap(namespace())
        check(aliasMap.putIfAbsent(value, this) == null) {
            "Conflict, there is already an enumeration value for '$value' registered: ${aliasMap[value]!!::class.simpleName}"
        }
        @Suppress("UNCHECKED_CAST")
        return this as SELF
    }

    final override fun toString(): String {
        var s = this.string
        if (s == null) {
            s = createString()
            this.string = s
        }
        return s
    }

    /**
     * The [value] converted to string, the same as [toString], just shorted notation in Kotlin.
     */
    val str: String
        get() = toString()

    fun toJSON(): String = toString()

    /**
     * Tests if this object is like the given value.
     * @param other the other value to compare against.
     * @return _true_ if the other value represents the same as this object; _false_ otherwise.
     */
    fun like(other: Any?): Boolean {
        if (this === other) return true
        if (other is JsEnum) return other.value == value
        return value == other
    }
    final override fun equals(other: Any?): Boolean = this === other || (other is JsEnum && other.value == value)
    final override fun hashCode(): Int = toString().hashCode()

    final override val length: Int
        get() = toString().length

    final override fun get(index: Int): Char = toString()[index]

    final override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = toString().subSequence(startIndex, endIndex)
}