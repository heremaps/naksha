@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport

/**
 * A multi-platform class.
 * @param <P> The platform type.
 * @param <T> The Naksha multi-platform type.
 */
@JsExport
abstract class NakClass<P, T : NakType<P>> {
    /**
     * Returns the symbol to which the class is bound.
     */
    abstract fun symbol(): PSymbol

    /**
     * Tests whether the given object can be cast into this type.
     * @param o The object to test.
     * @return _true_ if the object can be cast into this type; _false_ otherwise.
     */
    abstract fun canCast(o: Any?): Boolean

    /**
     * Tests whether the given object is an instance of this Naksha type.
     * @param o The object to test.
     * @return _true_ if the given object is already of this Naksha type; _false_ otherwise.
     */
    abstract fun isInstance(o: Any?): Boolean

    /**
     * Creates a new type instance bound to the given object.
     * @param o The object to bind the type to.
     * @return The new Naksha multi-platform type.
     */
    abstract fun create(o: P): T
}