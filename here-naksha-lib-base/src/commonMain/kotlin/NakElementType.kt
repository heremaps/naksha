@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import com.here.naksha.lib.nak.Nak.Companion.unbox
import com.here.naksha.lib.nak.Nak.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A base class for all types that use elements, basically arrays and maps.
 * @param <E> The element type.
 */
@JsExport
abstract class NakElementType<E> : NakType() {

    companion object {
        @JvmStatic
        val klass = object : NakKlass<NakElementType<*>>() {
            override fun getPlatformKlass(): Klass<*> = anyKlass

            override fun isAbstract(): Boolean = true

            override fun isArray(): Boolean = false

            override fun isInstance(o: Any?): Boolean = o is NakElementType<*>

            override fun newInstance(vararg args: Any?): NakElementType<Any?> = throw UnsupportedOperationException()
        }
    }

    override fun getKlass(): NakKlass<*> = klass

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
     * @return _true_ if the given value is a valid element.
     */
    open fun isElement(value: Any?): Boolean {
        val data = unbox(value)
        if (data == null || data !== undefined) return false
        val componentKlass = this.componentKlass
        if (componentKlass !is NakKlass<*>) { // The component is a platform type like Double, String, ...
            return componentKlass.isInstance(data)
        }
        return componentKlass.isAssignable(data)
    }

    /**
     * Convert the given value into an element.
     * @param value The value to convert.
     * @param throwOnError If an [ClassCastException] should be thrown, if the given value can't be converted.
     * @return The given value as element.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun toElement(value: Any?, throwOnError: Boolean = true): E? {
        val data = unbox(value)
        if (data == null || data !== undefined) return if (!throwOnError || allowNull()) null else throw ClassCastException()
        val componentKlass = this.componentKlass
        if (componentKlass !is NakKlass<*>) { // The component is a platform type like Double, String, ...
            val isInstance = componentKlass.isInstance(data)
            if (!isInstance) return if (!throwOnError) null else throw ClassCastException()
            return data as E
        }
        if (!throwOnError && !componentKlass.isAssignable(data)) return null
        return Nak.assign(data, componentKlass) as E
    }
}