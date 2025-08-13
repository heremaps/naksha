package naksha.base

import naksha.base.Platform.Platform_C.forClass
import naksha.base.Platform.Platform_C.logger
import naksha.base.Platform.Platform_C.unbox
import naksha.base.Platform.Platform_C.unsafe
import java.lang.invoke.MethodHandles
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

/**
 * The Java implementation of [JvmPlatformType].
 * @since 3.0
 */
internal class JvmPlatformType<T : Any> internal constructor(
    /**
     * The Java Class that represents this type, same as [nativeClass].
     * @since 3.0
     */
    val jvmClass: Class<T>,

    /**
     * The Kotlin klass representing this type.
     * @since 3.0
     */
    override val kotlinClass: KClass<T>
) : AbstractPlatformType<T>() {

    private var _superType: PlatformType<*>? = null
    override val superType: PlatformType<*>?
        get() {
            var type = _superType
            if (type != null) return type
            val superClass = jvmClass.superclass ?: return null
            if (superClass === Object::class.java) return null
            type = forClass(superClass)
            type.initialize()
            _superType = type
            return type
        }

    /**
     * The primitive type, if this has a primitive variant.
     * @since 3.0
     */
    val primitiveClass: Class<*>? = when (jvmClass) {
        Boolean::class.java -> Boolean::class.javaPrimitiveType
        Byte::class.java -> Byte::class.javaPrimitiveType
        Short::class.java -> Short::class.javaPrimitiveType
        Char::class.java -> Char::class.javaPrimitiveType
        Int::class.java -> Integer::class.javaPrimitiveType
        Long::class.java -> Long::class.javaPrimitiveType
        Float::class.java -> Float::class.javaPrimitiveType
        Double::class.java -> Double::class.javaPrimitiveType
        else -> null
    }

    companion object JvmPlatformType_C {
        private val lock: PlatformLock = Platform.newLock()
        private val initCache: AtomicMap<JvmPlatformType<*>, Boolean> = AtomicMap()

        @JvmField
        internal val jvmClassToPlatformType: AtomicMap<Class<*>, JvmPlatformType<*>> = AtomicMap()
        @JvmField
        internal val jsonTypeToPlatformType: AtomicMap<String, Array<PlatformType<*>>> = AtomicMap()
        private val ensureClassInitialized: Method? // unsafe.ensureClassInitialized
        private val lookupInstance: Any? // MethodHandles.lookup()
        private val ensureInitialized: Method? // MethodHandles.lookup().ensureInitialized(klass);

        init {
            var _ensureClassInitialized: Method?
            var _ensureInitialized: Method?
            var _lookupInstance: Any?
            try {
                // Note: Before Java 15, `MethodHandles.lookup().ensureInitialized(klass)` does not exist, we need to use Unsafe!
                _ensureClassInitialized = unsafe.javaClass.getMethod("ensureClassInitialized", Class::class.java)
                _lookupInstance = null
                _ensureInitialized = null
            } catch (ignore: NoSuchMethodException) {
                // In Java 23+ `Unsafe.ensureClassInitialized` does not exist, use `MethodHandles.lookup().ensureInitialized(klass)`!
                _ensureClassInitialized = null
                val lookupMethod = MethodHandles::class.java.getMethod("lookup")
                _lookupInstance = lookupMethod.invoke(null)
                _ensureInitialized = _lookupInstance.javaClass.getMethod("ensureInitialized", Class::class.java)
            }
            ensureClassInitialized = _ensureClassInitialized
            lookupInstance = _lookupInstance
            ensureInitialized = _ensureInitialized
        }

        private fun <T : Any> nonArgConstructorFor(jvmClass: Class<T>): Constructor<T>? {
            if (jvmClass.isInterface || Modifier.isAbstract(jvmClass.modifiers)) return null
            for (c in jvmClass.declaredConstructors) {
                @Suppress("UNCHECKED_CAST")
                if (c.parameters.isEmpty()) {
                    // Note: Package private means: not private, protected or public
                    //       We only prevent access to private!
                    if (Modifier.isPrivate(c.modifiers)) continue
                    // We try to open protected or internal constructors.
                    if (!Modifier.isPublic(c.modifiers)) {
                        try {
                            c.setAccessible(true)
                        } catch (_: SecurityException) {
                            continue
                        }
                    }
                    return c as Constructor<T>
                }
            }
            return null
        }
        private fun useAllocate(jvmClass: Class<*>): Boolean {
            if (jvmClass.isInterface || Modifier.isAbstract(jvmClass.modifiers)) return false
            return jvmClass.declaredConstructors.size == 0
        }

        private val NULL_PLATFORM_TYPE_ARRAY = Array<PlatformType<*>>(0) {
            throw illegalState("NULL_PLATFORM_TYPE_ARRAY cannot be larger than zero!")
        }
    }

    override val name: String = jvmClass.name
    override val packageName: String = jvmClass.packageName
    override fun withPackageName(packageName: String): PlatformType<T> {
        if (this.packageName == packageName) return this
        throw illegalArg("Invalid package name set: '$packageName', expected to be '${this.packageName}'")
    }
    override val simpleName: String = jvmClass.simpleName
    override var symbol: Symbol = Platform.DEFAULT_SYMBOL
    override fun withSymbol(symbol: Symbol): PlatformType<T> {
        this.symbol = symbol
        return this
    }
    override var jsonType: String? = null
        set(value) {
            if (field != value) {
                val old = field
                if (old != null) atomicMapArrayRemove(jsonTypeToPlatformType, old, this)
                if (value != null) atomicMapArrayAdd(jsonTypeToPlatformType, value, this)
                field = value
            }
        }
    override fun withJsonType(jsonType: String?): PlatformType<T> {
        this.jsonType = jsonType
        return this
    }
    override fun withNameAsJsonType(): PlatformType<T> = withJsonType(name)
    override val nativeClass: Any = jvmClass

    private val nonArgConstructor: Constructor<T>? = nonArgConstructorFor(jvmClass)
    private val useAllocate: Boolean = useAllocate(jvmClass)

    override val isInstantiatable: Boolean = nonArgConstructor != null || useAllocate

    override fun initialize(): PlatformType<T> {
        if (initCache[this] == true) return this
        // Not yet initialized.
        lock.acquire().use {
            // If we're the first entering the initialization phase, do it.
            if (initCache.putIfAbsent(this, true) == null) {
                // Avoid known initialization issue with Object, primitives and arrays, we anyway do not need to initialize them!
                if (jvmClass !== Object::class.java && !jvmClass.isPrimitive && !jvmClass.isArray) try {
                    // This code is required, because in Java 23 they removed unsafe.ensureClassInitialized, but
                    // the replacement method does not exist before Java 15, this is such a nonsense!
                    val _lookupInstance = lookupInstance
                    val _ensureInitialized = ensureInitialized
                    if (_ensureInitialized != null && _lookupInstance != null) {
                        _ensureInitialized.invoke(_lookupInstance, nativeClass)
                        // == MethodHandles.lookup().ensureInitialized(nativeType);
                    } else {
                        val _ensureClassInitialized = ensureClassInitialized
                        require(_ensureClassInitialized != null) { "Failed to use unsafe.ensureClassInitialized" }
                        _ensureClassInitialized.invoke(unsafe, nativeClass)
                        // == unsafe.ensureClassInitialized(nativeType)
                    }
                } catch (t: Throwable) {
                    // We ignore errors in initialization, it mainly happens for native classes.
                }
            }
        }
        return this
    }

    /**
     * Call the parameterless constructor of the type using platform specific methods.
     *
     * @return the new type.
     * @since 3.0
     */
    override fun newInstance(): T {
        val constructor = nonArgConstructor
        if (constructor == null && !useAllocate) throw illegalState("The class $name does not have an accessible parameterless constructor")
        try {
            if (constructor != null) return constructor.newInstance()
            return allocate()
        } catch (t: Throwable) {
            logger.error("Failed to invoke parameterless constructor of $name", t)
            var e = t
            var msg = e.message
            while (msg == null && e.cause != null) {
                e = e.cause!!
                msg = e.message
            }
            throw illegalState("Failed to invoke parameterless constructor of $name: $msg")
        }
    }

    /**
     * Create a new instance of the type, bypassing the constructor, so it returns the uninitialized class.
     *
     * @return the new type.
     * @since 3.0
     */
    @Suppress("UNCHECKED_CAST")
    override fun allocate(): T = unsafe.allocateInstance(jvmClass) as T

    /**
     * Tests if the [target] class or interface is either the same as, or is a superclass or superinterface of, the class or interface represented by this.
     *
     * For example, assume `fooType` is a `PlatformType<String>`, then `foo.isAssignableFrom(ofKotlin(CharSequence::class))` would be _false_, because not every [CharSequence] is always a [String].
     *
     * However, if `foo` is a `PlatformType<CharSequence>`, then `foo.isAssignableFrom(ofKotlin(String::class))` will be _true_, because every [String] is always a [CharSequence].
     *
     * In other words, this method tests if the [target] type can be cast _(widened)_ to this type, so if **`target as this`** is possible.
     *
     * **Warning**: An assignment is not the same as an instanceof test. For example for interfaces the example can be tricky, because formally the cast from a [CharSequence] to a [String] is not an assignable form, but technically can still succeed, if the object being tried to cast down is actually a string, just the compiler type is formally [CharSequence]. Formally this kind of cast is an assignment from [String] to [String] not being known at compile time.
     *
     * @param target The target type from which to cast.
     * @return _true_ if the [target] type can be cast to this type in all cases; _false_ otherwise.
     */
    override fun isAssignableFrom(target: PlatformType<*>): Boolean {
        val targetType = target as JvmPlatformType<*>
        val thisClass = this.jvmClass
        val thisPrimitiveClass = this.primitiveClass
        val targetClass = targetType.jvmClass
        return thisClass === targetClass || thisPrimitiveClass === targetClass || thisClass.isAssignableFrom(targetClass)

    }

    /**
     * Tests if this class or interface is either the same as, or is a superclass or superinterface of, the class or interface represented by [target].
     *
     * For example, assume `fooType` is a `PlatformType<CharSequence>`, then `foo.isAssignableTo(ofKotlin(String::class))` would be _false_, because not every [CharSequence] is always a [String].
     *
     * However, if `foo` is a `PlatformType<String>`, then `foo.isAssignableTo(ofKotlin(CharSequence::class))` will be _true_, because every [String] is always a [CharSequence].
     *
     * In other words, this method tests if this type can be cast _(widened)_ to the [target] type, so if **`this as target`** is possible.
     *
     * **Warning**: An assignment is not the same as an instanceof test. For example for interfaces the example can be tricky, because formally the cast from a [CharSequence] to a [String] is not an assignable form, but technically can still succeed, if the object being tried to cast down is actually a string, just the compiler type is formally [CharSequence]. Formally this kind of cast is an assignment from [String] to [String] not being known at compile time.
     *
     * @param target The target type to which to cast.
     * @return _true_ if this type can be cast to the [target] type in all cases; _false_ otherwise.
     */
    override fun isAssignableTo(target: PlatformType<*>): Boolean {
        val targetType = target as JvmPlatformType<*>
        val thisClass = this.jvmClass
        val thisPrimitiveClass = this.primitiveClass
        val targetClass = targetType.jvmClass
        return targetClass === thisClass || thisPrimitiveClass === targetClass || targetClass.isAssignableFrom(thisClass)
    }

    /**
     * Test if the given target is an instance of this type.
     * @param target the target to test.
     * @return _true_ if the given target is an instance of this type; _false_ otherwise.
     */
    override fun isInstance(target: Any?): Boolean {
        if (primitiveClass != null) return kotlinClass.isInstance(target)
        return jvmClass.isInstance(target)
    }

    private val _isProxy: Boolean = Proxy::class.isSuperclassOf(kotlinClass)

    override fun isProxy(): Boolean = _isProxy

    override fun proxy(o: PlatformObject?): T = getOrCreateProxy(o, symbol)

    @Suppress("UNCHECKED_CAST")
    override fun getProxy(o: PlatformObject?, symbol: Symbol): T? {
        if (isInstance(o)) return o as T
        if (!isProxy()) return null
        val jvmObject = unbox(o)
        if (jvmObject !is JvmObject) return null
        val proxy = jvmObject.getSymbol(symbol)
        if (proxy != null && isInstance(proxy)) return proxy as T
        return null
    }

    @Suppress("UNCHECKED_CAST")
    override fun getOrCreateProxy(o: PlatformObject?, symbol: Symbol): T {
        if (isInstance(o)) return o as T
        if (!isProxy()) throw illegalState("The type '$name' is no proxy-type")
        if (!isInstantiatable) throw illegalState("The type '$name' is not instantiatable")
        val jvmObject = unbox(o)
        if (jvmObject !is JvmObject) {
            throw illegalArg("This method requires the default platform-type implementation JvmObject, but ${o?.javaClass?.name ?: "null"} was given")
        }
        val existing = jvmObject.getSymbol(symbol)
        if (existing != null && isInstance(existing)) return existing as T

        val obj = newInstance()
        val proxy = (obj as Proxy)
        proxy.reBind = true
        proxy.bind(jvmObject, symbol)
        return obj
    }

    /**
     * Cast the given target into this type if possible.
     *
     * - [NakshaError.ILLEGAL_ARGUMENT] - if the given argument can't be cast to this type.
     * @param o the target to test.
     * @return the target as this type.
     */
    override fun cast(o: Any?): T? {
        if (o == null) return null
        if (jvmClass.isInstance(o)) return jvmClass.cast(o)
        throw illegalArg("Can't cast '${o.javaClass.name}' to '$name'")
    }
}