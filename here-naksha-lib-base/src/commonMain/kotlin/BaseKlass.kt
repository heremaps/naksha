@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport

/**
 * A Naksha Klass, which is an assignment class. The platform klass of an assignment klass must be either
 * [PObject], [PArray] or [PDataView].
 * @param <T> The type, must extend [BaseType].
 */
@JsExport
abstract class BaseKlass<out T : BaseType> : Klass<T>() {
    /**
     * Returns the platform type of this class, which must be one of: [PObject], [PArray] or [PDataView].
     * @return the platform type of this class.
     */
    abstract fun getPlatformKlass(): Klass<*>

    /**
     * Returns the symbol to which the type is bound by default.
     * @return platform symbol.
     */
    open fun symbol(): PSymbol = Base.BASE_SYM

    /**
     * Returns _true_ if this type is abstract (which means, no instance can be created, trying to do so will
     * raise an [UnsupportedOperationException].
     * @return _true_ if this type is abstract.
     */
    abstract fun isAbstract(): Boolean

    /**
     * Tests whether this type can be assigned to the given object. If the given object is an assignment type, the test
     * is executed against the data object (underlying platform type).
     * @param o The object to test.
     * @return _true_ if this type can be assigned to the given object; _false_ otherwise.
     */
    open fun isAssignable(o: Any?): Boolean = getPlatformKlass().isInstance(Base.unbox(o))

    /**
     * Returns the assignment of this class to the given data object. If the data object is assignable, but not yet correctly
     * assigned for the default symbol of this klass, creates a new instance and assigns it.
     * @param o The platform object to return this type for.
     * @return The new assignment instance.
     * @throws UnsupportedOperationException If this is an abstract class or the given data object is of a wrong type.
     */
    open fun assign(o: Any, vararg args: Any?): T = Base.assign(o, this, args)
}