package naksha.psql.executors.write

import naksha.psql.PgColumn
import naksha.psql.PgCursor

object PgCursorUtil {

    fun <T> PgCursor.collectAndClose(collector: (ReadOnlyRow) -> T): List<T> {
        val result = mutableListOf<T>()
        try {
            while (next()) {
                result.add(collector(ReadOnlyRow(this)))
            }
        } catch (e: Exception) {
            close()
            throw e
        }
        return result
    }

    class ReadOnlyRow(private val cursor: PgCursor) {
        fun column(column: PgColumn): Any? =
            cursor.column(column)

        operator fun <T : Any> get(column: PgColumn): T =
            cursor[column]
    }
}