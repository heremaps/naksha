package naksha.psql

import naksha.base.AnyObject
import naksha.base.Platform
import naksha.base.illegalState
import kotlin.math.max
import kotlin.reflect.KClass

actual class PgCursor private actual constructor() : AutoCloseable {

    /**
     * Invokes from [PgConnection.execute] for a virtual cursor with fixed results.
     * @since 3.0
     */
    internal constructor(results: Array<dynamic>) : this() {
        this.results = results
    }

    /**
     * Wrapper around a real cursor.
     * @since 3.0
     */
    internal constructor(cursor: Cursor) : this() {
        this.cursor = cursor
    }

    /**
     * Wrapper around an effected rows result.
     * @since 3.0
     */
    internal constructor(affectedRows: Int) : this() {
        this.affectedRows = affectedRows
    }

    private var results: Array<dynamic>? = null
    private var cursor: Cursor? = null
    private var affectedRows: Int = -1
    private var pos = -1
    private var value: dynamic? = null

    actual fun affectedRows(): Int
        = affectedRows

    actual fun next(): Boolean {
        fetch()
        return isRow()
    }

    actual fun fetch(): PgCursor {
        if (affectedRows >= 0) return this
        val cursor = this.cursor
        pos++
        if (cursor != null) {
            value = cursor.fetch()
        } else {
            val results = this.results
            value = if (results != null && pos >= 0 && pos < results.size) results[pos] else null
        }
        return this
    }

    actual fun isRow(): Boolean = value != null

    actual fun rowNumber(): Int = max(0, pos + 1)

    private var columnNames: Array<String>? = null

    @Suppress("UnusedVariable", "unused")
    actual fun columnNames(): Array<String> {
        var names = this.columnNames
        if (names != null) return names
        if (!isRow()) throw illegalState("The cursor is not positioned at a row")
        val value = this.value
        names = js("Object.keys(value)").unsafeCast<Array<String>>()
        this.columnNames = names
        return names
    }

    @Suppress("UnusedVariable", "unused")
    actual operator fun contains(name: String): Boolean {
        val value = this.value
        return js("Object.hasOwn(value, name)").unsafeCast<Boolean>()
    }

    @JsName("containsColumn")
    actual operator fun contains(column: PgColumn): Boolean = contains(column.name)

    actual fun column(name: String): Any? {
        val value = this.value ?: return null
        return value[name]
    }

    @JsName("columnValue")
    actual fun column(column: PgColumn): Any? = column(column.name)

    actual fun <T : Any> columnOr(name: String, alternative: T): T {
        val value = this.value ?: return alternative
        return if (alternative::class.isInstance(value)) value as T else alternative
    }

    @Suppress("UNCHECKED_CAST")
    actual operator fun <T : Any> get(name: String): T = column(name) as T

    @Suppress("UNCHECKED_CAST")
    @JsName("getByColumn")
    actual operator fun <T : Any> get(column: PgColumn): T = column(column.name) as T

    actual fun <T : AnyObject> map(@Suppress(names = ["NON_EXPORTABLE_TYPE"]) klass: KClass<T>): T {
        TODO("Not yet implemented")
    }

    actual override fun close() {
        if (affectedRows == -1) {
            val cursor = this.cursor
            this.cursor = null
            this.results = null
            this.value = null
            this.pos = -1
            if (cursor != null) cursor.close()
        }
    }
}