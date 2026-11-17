package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import org.mockito.Mockito
import java.sql.PreparedStatement
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgVerifiedNamesConcurrencyTest {
  @Test
  fun inconsistentTypesForAKeyAreAClassCastContractViolation() {
    val id = Id("conflict")
    val key = "naksha.psql.PgTableName"
    id.attributes[key] = object : IdAttribute(id, key) {}
    assertFailsWith<ClassCastException> { PgTableName.of(id).name }
  }

  @Test
  fun jdbcBindsTypedIdentifiersAsValuesNotObjectSerializations() {
    val id = Id("roads")
    val statement = Mockito.mock(PreparedStatement::class.java)
    val query = PsqlQuery("SELECT $1::text, $2::text, $3::text, $4::text", null)
    query.bindArguments(statement, arrayOf(id.databaseName(), id.schemaName(), id.tableName(), PgIndexName.of(id)))
    for (i in 1..4) Mockito.verify(statement).setString(i, "roads")
  }

  @Test
  fun concurrentFactoriesReturnTheSinglePublishedNamePerRole() {
    val id = Id("Concurrent-Roads")
    val executor = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)
    try {
      val tasks = ArrayList<Future<Unit>>()
      for (worker in 0 until 8) tasks.add(executor.submit(Callable {
        start.await()
        repeat(500) {
          val schema = PgSchemaName.of(id)
          val table = PgTableName.of(id)
          val index = PgIndexName.of(id)
          assertSame(schema, id.attributes[schema.key])
          assertSame(table, id.attributes[table.key])
          assertSame(index, id.attributes[index.key])
          assertEquals("Concurrent-Roads", schema.name)
        }
      }))
      start.countDown()
      for (task in tasks) task.get(20, TimeUnit.SECONDS)
      assertEquals(3, id.attributes.size)
    } finally {
      executor.shutdownNow()
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
    }
  }
}
