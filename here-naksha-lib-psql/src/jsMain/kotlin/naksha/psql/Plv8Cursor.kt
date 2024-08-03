package naksha.psql

import naksha.base.AnyObject
import kotlin.reflect.KClass

/**
 * A thin wrapper around [Plv8] to make it API compatible.
 */
class Plv8Cursor: PgCursor {
    override fun affectedRows(): Int {
        TODO("Not yet implemented")
    }

    override fun next(): Boolean {
        TODO("Not yet implemented")
    }

    override fun fetch(): PgCursor {
        TODO("Not yet implemented")
    }

    override fun isRow(): Boolean {
        TODO("Not yet implemented")
    }

    override fun rowNumber(): Int {
        TODO("Not yet implemented")
    }

    override fun contains(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun column(name: String): Any? {
        TODO("Not yet implemented")
    }

    override fun <T : Any> get(name: String): T {
        TODO("Not yet implemented")
    }

    override fun <T : AnyObject> map(klass: KClass<T>): T {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}