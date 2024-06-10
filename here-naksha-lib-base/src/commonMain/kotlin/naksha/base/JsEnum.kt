@file:Suppress("LeakingThis")

package naksha.base

import naksha.base.JsEnum.JsEnumCompanion.get
import naksha.base.Platform.PlatformCompanion.logger
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

    companion object JsEnumCompanion {
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
        @JvmStatic
        private val klassToNamespace = AtomicMap<KClass<out JsEnum>, KClass<out JsEnum>>()

        /**
         * All defined enumeration values of a namespace. The first level is the namespace (the Kotlin class that directly extend [JsEnum]), the second level maps values to defined enumeration instances.
         */
        @JvmStatic
        private val definedMap = AtomicMap<KClass<*>, AtomicMap<Any, JsEnum>>()

        /**
         * All defined enumeration aliases. The first level is the namespace (the Kotlin class that directly extend [JsEnum]), the second level maps alias values to defined enumeration instances.
         */
        @JvmStatic
        private val definedAliasesMap = AtomicMap<KClass<*>, AtomicMap<Any, JsEnum>>()

        /**
         * All temporary registered enumeration values and alias values of a namespace. The first level is the namespace (the Kotlin class that directly extend [JsEnum]), the second level maps main values to temporary registered instances.
         */
        @JvmStatic
        private val temporaryMap = AtomicMap<KClass<*>, AtomicMap<Any, WeakRef<JsEnum>>>()

        /**
         * Returns the defined map, so the assignment between the value and the defined enumeration instance.
         * @param ns the namespace to check.
         * @return the defined map.
         */
        @JvmStatic
        private fun defMap(ns: KClass<out JsEnum>): AtomicMap<Any, JsEnum> {
            var defMap = definedMap[ns]
            if (defMap == null) {
                defMap = AtomicMap()
                val existing = definedMap.putIfAbsent(ns, defMap)
                if (existing != null) defMap = existing
            }
            return defMap
        }

        /**
         * Returns the defined aliases map, so the assignment between the alias value and the defined enumeration instance.
         * @param ns the namespace to check.
         * @return the defined aliases map.
         */
        @JvmStatic
        private fun aliasMap(ns: KClass<out JsEnum>): AtomicMap<Any, JsEnum> {
            var aliasMap = definedAliasesMap[ns]
            if (aliasMap == null) {
                aliasMap = AtomicMap()
                val existing = definedAliasesMap.putIfAbsent(ns, aliasMap)
                if (existing != null) aliasMap = existing
            }
            return aliasMap
        }

        /**
         * Returns the temporary map, so the assignment between the value (or alias value), and the temporary enumeration instance.
         * @param ns the namespace to check.
         * @param create if a new namespace should be created.
         * @return the temporary map.
         */
        @JvmStatic
        private fun tempMap(ns: KClass<out JsEnum>, create: Boolean): AtomicMap<Any, WeakRef<JsEnum>>? {
            var tempMap = temporaryMap[ns]
            if (tempMap == null) {
                if (!create) return null
                tempMap = AtomicMap()
                val existing = temporaryMap.putIfAbsent(ns, tempMap)
                if (existing != null) tempMap = existing
            }
            return tempMap
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
        @JvmStatic
        @JsStatic
        fun <ENUM : JsEnum> def(enumKlass: KClass<ENUM>, value: Any?, init: Fx1<ENUM>? = null): ENUM {
            require(value === null || value is String || value is Number || value is Int64) {
                "Invalid enumeration value, require null, String or Number"
            }
            val e = __get(value, enumKlass, false)
            init?.call(e)
            return e
        }

        /**
         * Returns the enumeration instance for the given value and namespace. If the value is pre-defined, the singleton is returned, otherwise a new instance is created.
         * @param value The value for which to return the enumeration.
         * @param enumKlass The enumeration klass to query.
         * @return The enumeration for the given value.
         */
        @JvmStatic
        @JsStatic
        fun <ENUM : JsEnum> get(value: Any?, enumKlass: KClass<out ENUM>): ENUM = __get(value, enumKlass, true)

        /**
         * Returns the defined enumeration instance for the given value and namespace.
         * @param value the value for which to return the enumeration.
         * @param enumKlass the enumeration klass to query.
         * @return the defined enumeration for the given value or _null_, if the value does not exist in a defined form.
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <ENUM : JsEnum> getDefined(value: Any?, enumKlass: KClass<out ENUM>): ENUM? {
            val alignedValue = alignValue(value)
            val key = alignedValue ?: NULL
            val ns = klassToNamespace[enumKlass] ?: return null
            val mainMap = defMap(ns)
            var e = mainMap[key]
            if (e == null) {
                val aliasMap = aliasMap(ns)
                e = aliasMap[key]
                if (e == null && key is String) {
                    e = aliasMap[key.lowercase()]
                }
            }
            if (enumKlass.isInstance(e)) return e as ENUM?
            return null
        }

        /**
         * Internally called by [def] and [get] to return enumeration values.
         *
         * If [temporary] is set to _true_, then, if no such enumeration value does yet exist, create it as temporary. If [temporary] is _false_ and the value exists as temporary, then move it into the defined section, and return it.
         * @param value the value to query.
         * @param enumKlass the enumeration class to query.
         * @param temporary if the value is temporary only.
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        private fun <ENUM : JsEnum> __get(value: Any?, enumKlass: KClass<out ENUM>, temporary: Boolean): ENUM {
            // Optimistic locking, when something goes wrong, because another thread acts at the same time, we simply
            // restart the whole operation from start.
            val alignedValue = alignValue(value)
            val key = alignedValue ?: NULL
            while (true) {
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
                val mainMap = defMap(ns)
                var e = mainMap[key]
                if (e == null) {
                    val aliasMap = aliasMap(ns)
                    e = aliasMap[key]
                    if (e == null && key is String) {
                        e = aliasMap[key.lowercase()]
                    }
                }
                if (e == null) {
                    val tempMap = tempMap(ns, false)
                    if (tempMap != null) {
                        val ref = tempMap[key]
                        if (ref != null) {
                            e = ref.deref()
                            if (e == null) {
                                // Just cleanup, this means, there is simply no such enumeration value.
                                if (!tempMap.remove(ns, ref)) continue
                            } else {
                                check(enumKlass.isInstance(e)) {
                                    // TODO: KotlinCompilerBug: We know at this point, that "e" is not null!
                                    //       Why are we asked to use !! ?
                                    "Conflict in enumeration value '$alignedValue', there is an existing enumeration class (${e!!::class.simpleName}, but class ${enumKlass.simpleName} was requested"
                                }
                                if (temporary) return e as ENUM // Great, we wanted a temporary one.

                                // We should define the enumeration value, which currently is temporary.
                                // Add into permanent map, then remove from temporary map.
                                val defMap = defMap(ns)
                                val existingDefined = defMap.putIfAbsent(key, e)
                                if (!tempMap.remove(ns, ref)) {
                                    logger.warn(
                                        "Failed to remove namespace ${ns.simpleName} from temporary map, after already adding it into defined map, this must not happen!"
                                    )
                                }
                                if (existingDefined == null) return e as ENUM
                                check(enumKlass.isInstance(existingDefined)) {
                                    "Conflict in enumeration value '$alignedValue', there is an existing enumeration class (${existingDefined::class.simpleName}, but class ${enumKlass.simpleName} was requested"
                                }
                                return existingDefined as ENUM
                            }
                        }
                    }
                }
                // No existing value yet.
                if (e == null) {
                    // The value is not pre-defined, create it on-the-fly.
                    e = Platform.allocateInstance(enumKlass)
                    e.value = alignedValue
                    if (temporary) {
                        val tempMap = tempMap(ns, true)!!
                        // If the key is already assigned, another thread was faster, repeat all of this!
                        if (tempMap.putIfAbsent(key, WeakRef(e)) != null) continue
                        e.init()
                    } else {
                        e.isDefined = true
                        val defMap = defMap(ns)
                        // If the key is already assigned, another thread was faster, repeat all of this!
                        if (defMap.putIfAbsent(key, e) != null) continue
                        e.init()
                    }
                } else {
                    check(enumKlass.isInstance(e)) {
                        "Conflict in enumeration value '$value', there is an existing enumeration class (${e::class.simpleName}, but class ${enumKlass.simpleName} was requested"
                    }
                }
                return e as ENUM
            }
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
     * Runs a lambda against this enumeration instance, to be used like:
     * ```
     * class Foo : JsEnum() {
     *   companion object FooComp {
     *     @JsStatic
     *     @JvmStatic
     *     val DEMO = def(Foo::class, "demo").with<Foo>() { self ->
     *       self.property = "value"
     *     }
     *   }
     *   ...
     * }
     * ```
     * In most cases it is better to use the lambda of [def] or [defIgnoreCase], which has exactly the same effect:
     * ```
     * class Foo : JsEnum() {
     *   companion object FooComp {
     *     @JsStatic
     *     @JvmStatic
     *     val DEMO = def(Foo::class, "demo") { self ->
     *       self.property = "value"
     *     }
     *   }
     *   ...
     * }
     * ```
     *
     * @param lambda the lambda to call with the first parameter being this.
     * @return this.
     */
    @Suppress("UNCHECKED_CAST")
    fun <SELF : JsEnum> with(lambda: Fx1<SELF>): SELF {
        lambda.call(this as SELF)
        return this
    }

    /**
     * Can be used with defined values to add aliases, to be used like:
     * ```
     * class Foo : JsEnum() {
     *   companion object FooComp {
     *     @JsStatic
     *     @JvmStatic
     *     val DEMO = def(Foo::class, "demo").alias<Foo>("DMO")
     *   }
     *   ...
     * }
     * ```
     *
     * @param value     The additional value to register.
     * @param <SELF>    The type of this.
     * @return this.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <SELF : JsEnum> alias(value: Any): SELF {
        // Optimistic locking
        while (true) {
            val ns = namespace()
            val key = alignValue(value) ?: NULL
            if (isDefined) {
                val aliasMap = aliasMap(ns)
                val existing = aliasMap.putIfAbsent(key, this)
                check(existing == null && existing !== this) {
                    // TODO: KotlinCompilerBug - It should know that existing is not null here!
                    "Conflict, there is already an enumeration value for '$value' registered: ${existing!!::class.simpleName}"
                }
            } else {
                val tempMap = tempMap(ns, true)!!
                val ref = tempMap.putIfAbsent(key, WeakRef(this))
                if (ref != null) {
                    val existing = ref.deref()
                    if (existing == null) {
                        tempMap.remove(key, ref)
                        continue
                    }
                    check(existing !== this) {
                        // TODO: KotlinCompilerBug - It should know that existing is not null here!
                        "Conflict, there is already an enumeration value for '$value' registered: ${existing::class.simpleName}"
                    }
                }
            }
            return this as SELF
        }
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