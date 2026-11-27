package naksha.psql

import naksha.base.Id
import naksha.base.IdAttribute
import naksha.base.NakshaException
import naksha.base.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PgVerifiedNamesTest {
  @Test
  fun eachRoleHasOneCachedAttributeAndStablePayload() {
    val id = Id("Roads")
    val database = PgDatabaseName.of(id)
    val schema = PgSchemaName.of(id)
    val table = PgTableName.of(id)
    val index = PgIndexName.of(id)
    assertEquals("naksha.psql.PgDatabaseName", database.key)
    assertEquals("naksha.psql.PgSchemaName", schema.key)
    assertEquals("naksha.psql.PgTableName", table.key)
    assertEquals("naksha.psql.PgIndexName", index.key)
    repeat(20) {
      assertSame(database, id.databaseName())
      assertSame(schema, id.schemaName())
      assertSame(table, id.tableName())
      assertSame(index, PgIndexName.of(id))
    }
    assertEquals("Roads", database.name)
    assertEquals("Roads", schema.name)
    assertEquals("Roads", table.name)
    assertEquals("\"Roads\"", schema.quotedName)
    val attributes = id.attributes
    for ((key, attr) in attributes) {
      assertSame(id, (attr as IdAttribute).id)
      assertEquals(key, attr.key)
    }
    assertEquals(4, attributes.size)
    assertEquals(schema, PgSchemaName.of(Id("Roads")))
    assertEquals(table.hashCode(), PgTableName.of(Id("Roads")).hashCode())
  }

  @Test
  fun checkedPhysicalNamesDoNotApplyTheLogicalAliasPolicy() {
    val schema = PgSchemaName.checked("a".repeat(63))
    assertEquals("naksha.psql.PgSchemaName.physical", schema.key)
    assertEquals("a".repeat(63), schema.name)
    assertTrue(PgSchemaName.of(schema.id).name.startsWith("s\$"))
    assertNotSame(schema, PgSchemaName.of(schema.id))
    val quoted = PgTableName.checked("table\"with quote")
    assertEquals("\"table\"\"with quote\"", quoted.quotedName)
    assertFailsWith<NakshaException> { PgTableName.of(quoted.id) }
    for (name in listOf("", "invalid\u0000name", "a".repeat(64), "é".repeat(32), "\ud800", "\udc00")) {
      assertFailsWith<NakshaException> { PgSchemaName.checked(name) }
      assertFailsWith<NakshaException> { PgDatabaseName.checked(name) }
    }
    for (name in listOf("", "invalid\u0000name", "a".repeat(30), "é".repeat(15), "\ud800", "\udc00")) {
      assertFailsWith<NakshaException> { PgTableName.checked(name) }
    }
  }

  @Test
  fun verifiedRootBudgetsProveAllGeneratedNamesFit() {
    val table = PgTableName.checked("é".repeat(14) + "a")
    val index = PgIndexName.of(Id("long index", Int.MIN_VALUE.toLong(), skipVerification = true))
    assertEquals(29, table.name.encodeToByteArray().size)
    assertEquals(13, index.name.encodeToByteArray().size)
    assertTrue("${table.headName}\$255\$${index.name}".encodeToByteArray().size <= PG_IDENTIFIER_BYTES)
    for (shift in 12..41) {
      for (slot in 0..255) {
        val partition = table.historyName(Version.HEAD.number - 1, shift, slot)
        assertTrue(partition.encodeToByteArray().size <= PG_IDENTIFIER_BYTES - 1 - PG_INDEX_ALIAS_BYTES)
        assertTrue(index.onHistoryTable(table, Version.HEAD.number - 1, shift, slot).encodeToByteArray().size <= PG_IDENTIFIER_BYTES)
      }
    }
    assertFailsWith<NakshaException> { table.historyName(-1, 12) }
    assertFailsWith<NakshaException> { table.historyName(Version.HEAD.number, 12) }
    assertFailsWith<NakshaException> { table.historyName(0, 42) }
    assertFailsWith<NakshaException> { table.historyName(0, 12, 256) }
  }

  @Test
  fun aForeignOwnerCannotPoisonTheVerifiedNameCache() {
    val id = Id("conflict")
    val foreign = PgTableName.of(Id("other-owner"))
    id.attributes[foreign.key] = foreign
    assertFailsWith<NakshaException> { PgTableName.of(id) }
  }

  @Test
  fun parameterBindingUnwrapsNamesWithoutMutatingCallerArguments() {
    val id = Id("roads")
    val args = arrayOf<Any?>(id.databaseName(), id.schemaName(), id.tableName(), PgIndexName.of(id), 42L)
    val bound = pgNameArguments(args)
    assertNotSame(args, bound)
    assertTrue(args[0] is PgDatabaseName)
    assertEquals(listOf("roads", "roads", "roads", "roads", 42L), bound.toList())
    assertSame(bound, pgNameArguments(bound))
  }
}
