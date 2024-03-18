@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The prepared plan as returned by the PLV8 engine.
 */
@JsExport
interface IPlv8Plan {
    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return Either the number of affected rows or the rows.
     */
    fun execute(args: Array<Any?>? = null): Any

    /**
     * Execute the prepared plan with the given arguments. The types must match to the prepared statement.
     * @param args The arguments to be set at $n position, where $1 is the first array element.
     * @return A cursor into the result-set.
     */
    fun cursor(args: Array<Any?>? = null): IPlv8Cursor

    /**
     * Adds string value to statement
     * @param parameterIndex Column index
     * @param value String value to set
     */
    fun setString(parameterIndex: Int, value: String?)

    /**
     * Adds byte[] value to statement
     * @param parameterIndex Column index
     * @param value Byte[] value to set
     */
    fun setBytes(parameterIndex: Int, value: ByteArray?)

    /**
     * Adds long value to statement
     * @param parameterIndex Column index
     * @param value Long value to set
     */
    fun setLong(parameterIndex: Int, value: BigInt64?)

    /**
     * Adds int value to statement
     * @param parameterIndex Column index
     * @param value int value to set
     */
    fun setInt(parameterIndex: Int, value: Int?)

    /**
     * Adds short value to statement
     * @param parameterIndex Column index
     * @param value short value to set
     */
    fun setShort(parameterIndex: Int, value: Short?)

    /**
     * Adds next batch element to statement.
     */
    fun addBatch()

    /**
     * Executes prepared batch and returns number of updated rows
     */
    fun executeBatch():IntArray

    /**
     * Frees the plan.
     */
    fun free()
}