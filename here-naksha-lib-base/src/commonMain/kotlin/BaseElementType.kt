@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Base.Companion.unbox
import com.here.naksha.lib.base.Base.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A base class for all types that use elements, basically arrays and maps.
 * @param <E> The element type.
 */
@JsExport
abstract class BaseElementType<E> : BaseType() {

    companion object {
        @JvmStatic
        val klass = object : BaseKlass<BaseElementType<*>>() {
            override fun getPlatformKlass(): Klass<*> = anyKlass

            override fun isAbstract(): Boolean = true

            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is BaseElementType<*>

            override fun newInstance(vararg args: Any?): BaseElementType<Any?> = throw UnsupportedOperationException()
        }
    }

    override fun getKlass(): BaseKlass<*> = klass

    /**
     * Returns the component klass (the klass of the elements stored in this array).
     * @return the component klass (the klass of the elements stored in this array).
     */
    @Suppress("UNCHECKED_CAST", "MemberVisibilityCanBePrivate")
    var componentKlass: Klass<E> = Klass.anyKlass as Klass<E>

    /**
     * Returns _true_ if _null_ elements are allowed.
     * @return _true_ if _null_ elements are allowed; _false_ otherwise.
     */
    open fun allowNull(): Boolean = true

    /**
     * Tests if the given value is a valid element of this type.
     * @param value The value to test.
     * @param klass The type to test for.
     * @return _true_ if the given value is a valid element.
     */
    open fun isElement(value: Any?, klass: Klass<*> = this.componentKlass): Boolean {
        val data = unbox(value)
        if (data == null || data !== undefined) return false
        if (klass !is BaseKlass<*>) { // The component is a platform type like Double, String, ...
            return klass.isInstance(data)
        }
        return klass.isAssignable(data)
    }

    /**
     * Convert the given value into an element.
     * @param value The value to convert.
     * @param klass The klass into which to convert.
     * @param alt The alternative to return, the value is [undefined] or _null_.
     * @return The given value as element.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun <T>toElement(value: Any?, klass: Klass<T>, alt: T? = null): T? {
        val data = unbox(value)
        if (data === null || data === undefined) return alt
        if (klass !is BaseKlass<*>) return if (klass.isInstance(data)) data as T else alt
        return if (klass.isAssignable(data)) Base.assign(data, klass) as T else alt
    }

}