package naksha.psql

import java.lang.reflect.Proxy
import java.sql.Statement
import java.sql.Types
import javax.sql.rowset.RowSetMetaDataImpl
import javax.sql.rowset.RowSetProvider
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PgNamingCursorTest {
    @Test
    fun readsPhysicalLabelsThroughLogicalRowAndDescriptorAccess() {
        val column = PgColumn(3, Member("RoadName", MemberType.STRING))
        val data = RowSetProvider.newFactory().createCachedRowSet()
        val metadata = RowSetMetaDataImpl()
        metadata.columnCount = 1
        metadata.setColumnName(1, column.columnName)
        metadata.setColumnLabel(1, column.columnName)
        metadata.setColumnType(1, Types.VARCHAR)
        metadata.setColumnTypeName(1, "text")
        data.setMetaData(metadata)
        data.moveToInsertRow()
        data.updateString(1, "Main Street")
        data.insertRow()
        data.moveToCurrentRow()
        data.beforeFirst()
        val statement = Proxy.newProxyInstance(Statement::class.java.classLoader, arrayOf(Statement::class.java)) { _, method, _ ->
            when (method.name) {
                "getUpdateCount" -> -1
                "getResultSet" -> data
                "getMoreResults" -> false
                "close" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Statement
        PgCursor(statement, true).fetch().use { cursor ->
            assertTrue(column in cursor)
            assertEquals("Main Street", cursor.column(column))
            assertEquals("Main Street", cursor.get<String>(column))
            val rows = PgRows().addColumn(column)
            assertTrue(rows.read(cursor))
            assertEquals("Main Street", rows.getString(0, column.name))
            assertEquals("Main Street", rows.columns[0].toArray()[0])
        }
    }
}
