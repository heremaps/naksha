package naksha.psql

import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgPlanInitializationTest {
  @Test
  fun preparesOnConstructionAndClosesOnlyOwnedResources() {
    val calls = ArrayList<String>()
    val resultSet = Proxy.newProxyInstance(ResultSet::class.java.classLoader, arrayOf(ResultSet::class.java)) { _, method, _ ->
      when (method.name) {
        "close" -> { calls.add("resultSet.close"); null }
        else -> throw UnsupportedOperationException(method.name)
      }
    } as ResultSet
    val statement = Proxy.newProxyInstance(PreparedStatement::class.java.classLoader, arrayOf(PreparedStatement::class.java)) { _, method, _ ->
      when (method.name) {
        "execute" -> { calls.add("execute"); true }
        "getUpdateCount" -> -1
        "getResultSet" -> resultSet
        "getMoreResults" -> false
        "close" -> { calls.add("statement.close"); null }
        else -> throw UnsupportedOperationException(method.name)
      }
    } as PreparedStatement
    val query = PsqlQuery("SELECT \$1::text", null)
    val connection = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
      when (method.name) {
        "prepareStatement" -> {
          assertEquals(listOf(query.sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT), args!!.toList())
          calls.add("prepare")
          statement
        }
        else -> throw UnsupportedOperationException(method.name)
      }
    } as Connection

    val plan = PgPlan(query, connection)
    assertSame(query, plan.query)
    assertSame(connection, plan.conn)
    assertSame(statement, plan.stmt)
    assertEquals(listOf("prepare"), calls)
    assertFalse(plan.closed)
    plan.use {
      it.execute().use { }
      assertEquals(listOf("prepare", "execute", "resultSet.close"), calls)
      assertFalse(it.closed)
    }
    plan.close()
    assertTrue(plan.closed)
    assertEquals(listOf("prepare", "execute", "resultSet.close", "statement.close"), calls)
  }
}
