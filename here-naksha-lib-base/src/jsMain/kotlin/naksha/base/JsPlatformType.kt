@file:OptIn(ExperimentalJsReflectionCreateInstance::class)
@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.DEFAULT_SYMBOL
import naksha.base.Platform.Platform_C.forInstance
import naksha.base.Platform.Platform_C.forJsClass
import naksha.base.Platform.Platform_C.isPlatformObject
import naksha.base.Platform.Platform_C.unbox
import kotlin.reflect.KClass
import kotlin.reflect.createInstance

/**
 * A platform type wrapper that wraps a native type, for example a Java `Class`, so that it works cross-platform, where types are needed.
 *
 * @since 3.0
 */
@JsExport
class JsPlatformType<T : Any> internal constructor(
    /**
     * The JavaScript class, which basically is just the constructor, so a function. This is the same as [nativeClass].
     * @since 3.0
     */
    val jsClass: JsClass<T>
) : AbstractPlatformType<T>() {
    companion object JsPlatformType_C {
        /**
         * The platform type by name.
         * @since 3.0
         */
        internal val byName: HashMap<String, JsPlatformType<*>> = HashMap()

        /**
         * The platform types by JSON `type`.
         * @since 3.0
         */
        internal val byJsonType: AtomicMap<String, Array<JsPlatformType<*>>> = AtomicMap()

        /**
         * A map between multiple _source-types_ and a potential _key-type_.
         *
         * Actually, the _key-type_ is the type into which the _source-type_ should be cast. The value is another map, where for every possible _source-type_ the value `true` is stored, if the _source-type_ can be cast down into the _key-type_; `false` if this is not possible; `null` if the expression is not yet evaluated.
         *
         * Eventually, this is used to test for `source-type as key-type`.
         */
        private val assignableTo = HashMap<JsPlatformType<*>, HashMap<JsPlatformType<*>, Boolean>>()

        /**
         * Tests if the `_from` type can be cast into `_to`.
         * @param _from the type that should be cast.
         * @param _to the type into which [_from] should be cast.
         * @return _true_ if the cast is supported; _false_ otherwise.
         */
        private fun isAssignable(_from: JsPlatformType<*>?, _to: JsPlatformType<*>?): Boolean {
            if (_from === _to) return true
            if (_from == null || _to == null) return false
            // _from as _to
            // _from=CharSequence, _to=String --> false   // isAssignable(String, CharSequence)
            // _from=String, _to=CharSequence --> true   // isAssignable(CharSequence, String)
            val assignableTo = this.assignableTo
            var assignable_from: HashMap<JsPlatformType<*>, Boolean>? = assignableTo[_to]
            if (assignable_from == null) {
                assignable_from = HashMap()
                assignableTo[_to] = assignable_from
            }
            var isAssignable: Boolean? = assignable_from[_from]
            if (isAssignable == null) {
                // Note: `_from.allocate()` will work as well for abstract and interfaces, as it does not invoke constructor!
                val _fromInstance = _from.allocate()
                isAssignable = _to.isInstance(_fromInstance)
                assignable_from[_from] = isAssignable
            }
            return isAssignable
        }

    }

    private val objectConstructor = js("Object").getPrototypeOf(js("Object"))
    private var _superType: PlatformType<*>? = null
    override val superType: PlatformType<*>?
        get() {
            var type = _superType
            if (type != null) return type
            val proto = js("Object").getPrototypeOf(this.jsClass).unsafeCast<JsClass<*>?>() ?: return null
            if (proto === objectConstructor) return null
            type = forJsClass(proto)
            type.initialize()
            _superType = type
            return type
        }

    @Suppress("UselessCallOnNotNull")
    override var name: String = ""
        get() {
            val name = field
            if (!name.isNullOrEmpty()) return name
            initialize()
            return field
        }
        internal set(value) {
            if (field != value) {
                val existing = field
                if (existing.isNullOrEmpty()) byName.remove(existing)

                val arr: dynamic = js("value.split('.')")
                if (arr.length < 2) throw illegalArg("Invalid name given, no package found: $name")
                val fullName = arr.join(".").unsafeCast<String>()
                val simpleName = arr.pop().unsafeCast<String>()
                val packageName = arr.join(".").unsafeCast<String>()

                field = fullName
                this.packageName = packageName
                this.simpleName = simpleName
                this.symbol = symbol
                byName[fullName] = this
            }
        }
    override var packageName: String = ""
        get() {
            val packageName = field
            @Suppress("UselessCallOnNotNull")
            if (!packageName.isNullOrEmpty()) return field
            initialize()
            return field
        }
        private set
    override fun withPackageName(packageName: String): PlatformType<T> {
        this.name = "$packageName.$simpleName"
        return this
    }
    override var simpleName: String = jsClass.name
        private set
    override var symbol: Symbol = DEFAULT_SYMBOL
    override fun withSymbol(symbol: Symbol): PlatformType<T> {
        this.symbol = symbol
        return this
    }
    override var jsonType: String? = null
        set(value) {
            if (field != value) {
                val old = field
                if (old != null) atomicMapArrayRemove(byJsonType, old, this)
                if (value != null) atomicMapArrayAdd(byJsonType, value, this)
                field = value
            }
        }
    override fun withJsonType(jsonType: String?): PlatformType<T> {
        this.jsonType = jsonType
        return this
    }
    override fun withNameAsJsonType(): PlatformType<T> = withJsonType(name)
    override val nativeClass: Any = jsClass
    @Suppress("NON_EXPORTABLE_TYPE")
    override val kotlinClass: KClass<T> = jsClass.kotlin

    private var isInitialized: Boolean = false

    private var _isInstantiatable: Boolean? = null
    override val isInstantiatable: Boolean
        get() {
            return _isInstantiatable ?: try {
                newInstance()
                _isInstantiatable = true
                true
            } catch (_: Throwable) {
                _isInstantiatable = false
                false
            }
        }

    @Suppress("UNUSED_PARAMETER")
    private fun findPackageName(c: JsClass<*>, path: dynamic, stack: dynamic, self: dynamic): Array<String>? =
// function findPackageName(c, path, stack, self) {
js("""
    for (var key in self) {
        try {
            var value = self[key];
            if (path.length > 0 && value === c) {
                console.log("found "+path.join(".")+"."+key);
                return path;
            }
            if (Object.prototype.toString.call(value) === "[object Object]") {
                var recursion = false;
                var j = 0;
                while (j < stack.length) {
                    var o = stack[j++];
                    if (value === o) {
                        recursion = true;
                        break;
                    }
                }
                if (!recursion) {
                    path.push(key);
                    stack.push(value);
                    var found = findPackageName(c, path, stack, value);
                    if (found != null) return found;
                    path.pop();
                    stack.pop();
                }
            }
        } catch (e) {}
    }
    return null;
""").unsafeCast<Array<String>?>()
    // var c = function Foo(){}; globalThis["com"]={here:{naksha:{bar:{Foo: c}}}};
    // findPackageName(c, [], [], globalThis)

    @Suppress("UselessCallOnNotNull")
    override fun initialize(): PlatformType<T> {
        if (!isInitialized) {
            // We need to set here, so that we do not end up in recursive calls.
            isInitialized = true
            try {
                kotlinClass.createInstance()
            } catch (_: Exception) {}
            if (packageName.isNullOrEmpty()) {
                val path = findPackageName(jsClass, js("[]"), js("[]"), js("globalThis"))
                // TODO: This happens in the IntelliJ debugger, where we do not have our own loader!
                // TODO: We need to improve this by injecting the information using gradle build!
                val packageName = path?.joinToString { "." } ?: "com.here.naksha"
                name = "$packageName.$simpleName"
                symbol = DEFAULT_SYMBOL
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
        try {
            return kotlinClass.createInstance()
        } catch (e: Throwable) {
            val msg = e.message
            if (msg != null && msg.contains("should have a single no-arg constructor")) {
                // If the constructor is protected or internal, we end up here.
                // For the sake of compatibility with Java, we simply bypass restrictions and try to invoke
                // the constructor without arguments, only if that fails, we return an error.
                try {
                    // This is very important, we need to ensure that the companion object is initialized!
                    initialize()
                    // Now we can call the constructor directly.
                    val constructor = nativeClass
                    return js("new constructor()").unsafeCast<T>()
                } catch (_: Throwable) {}
            }
            throw illegalState("The class $name does not have a parameterless constructor")
        }
    }

    /**
     * Create a new instance of the type, bypassing the constructor, so it returns the uninitialized class.
     *
     * @return the new type.
     * @since 3.0
     */
    override fun allocate(): T {
        // We can bypass the constructor, but before we do this, we need to ensure that the companion object
        // is created, and all other things of the class are ready. We can only do this by initializing the class!
        initialize()
        @Suppress("UNUSED_VARIABLE") //br
        val constructor = jsClass
        return js("Object.create(constructor.prototype)").unsafeCast<T>()
    }

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
    override fun isAssignableFrom(target: PlatformType<*>): Boolean = isAssignable(target.unsafeCast<JsPlatformType<*>>(), this)

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
    override fun isAssignableTo(target: PlatformType<*>): Boolean = isAssignable(this, target.unsafeCast<JsPlatformType<*>>())

    private fun getPrimitiveChecker(): dynamic = when (kotlinClass) {
        Boolean::class -> js("""function(o) { return o != null && Object.prototype.toString.apply(o) == "[object Boolean]" }""")
        Byte::class, Short::class, Int::class -> js("""function(o) { return o != null && Object.prototype.toString.apply(o) == "[object Number]" }""")
        Int64::class -> js("""function(o) { return o != null && Object.prototype.toString.apply(o) == "[object BigInt]" }""")
        else -> null
    }

    private val _isPrimitiveInstance: dynamic = getPrimitiveChecker()

    /**
     * Test if the given target is an instance of this type.
     * @param target the target to test.
     * @return `true` if the given target is an instance of this type; `false` otherwise.
     */
    override fun isInstance(target: Any?): Boolean {
        if (_isPrimitiveInstance != null) return _isPrimitiveInstance(target).unsafeCast<Boolean>()
        return kotlinClass.isInstance(target)
    }

    private var _isProxy: Boolean? = null

    override fun isProxy(): Boolean {
        var isProxy = _isProxy
        if (isProxy == null) {
            isProxy = isAssignableTo(Proxy.TYPE)
            _isProxy = isProxy
        }
        return isProxy
    }

    override fun proxy(o: PlatformObject?): T = getOrCreateProxy(o, symbol)

    @Suppress("UNCHECKED_CAST")
    override fun getProxy(o: PlatformObject?, symbol: Symbol): T? {
        if (isInstance(o)) return o as T
        if (!isProxy()) return null
        val raw = unbox(o).asDynamic()
        if (!isPlatformObject(raw)) return null
        val proxy = raw[symbol]
        if (isInstance(proxy)) return proxy.unsafeCast<T>()
        return null
    }

    @Suppress("UNCHECKED_CAST", "UnsafeCastFromDynamic")
    override fun getOrCreateProxy(o: PlatformObject?, symbol: Symbol): T {
        if (isInstance(o)) return o as T
        if (!isProxy()) throw illegalState("The type '$name' is no proxy-type")
        if (!isInstantiatable) throw illegalState("The type '$name' is not instantiatable")
        val raw = unbox(o).asDynamic()
        if (!isPlatformObject(raw)) throw illegalArg("Only platform objects can have a proxy")
        var proxy: dynamic = raw[symbol]
        if (isInstance(proxy)) return proxy.unsafeCast<T>()

        proxy = newInstance().unsafeCast<Proxy>()
        proxy.reBind = true
        proxy.bind(raw, symbol)
        return proxy
    }

    /**
     * Cast the given target into this type if possible.
     * @param o the target to test.
     * @return the target as this type.
     * @throws IllegalArgumentException if the given argument can't be cast to this type.
     */
    @Suppress("UNCHECKED_CAST")
    override fun cast(o: Any?): T? {
        if (o == null) return null
        if (isInstance(o)) return o as T
        throw IllegalArgumentException("Can't cast '${o::class.js.name}' to '$name'")
    }
}