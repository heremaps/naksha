package naksha.psql

import naksha.base.Id
import naksha.model.Tuple
import naksha.model.objects.NakshaAdmin
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.Id as IdMember
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Gt
import naksha.model.request.ops.Gte
import naksha.model.request.ops.IsAnyOf
import naksha.model.request.ops.Lt
import naksha.model.request.ops.Lte
import naksha.model.request.ops.Not
import naksha.model.request.ops.Op
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PgOrdinalQueryTest {
  @Test
  fun featureNumberEqualityAndInequalityUseRawColumnAndOrdinalArguments() {
    for (number in longArrayOf(Long.MIN_VALUE, -1L, 0L, 1L, 128L, 255L, 256L, Long.MAX_VALUE)) {
      for (negate in listOf(false, true)) {
        val equals = Equals(FeatureNumber, number)
        val clause = buildWhere(if (negate) Not(equals) else equals)
        assertEquals("\"fn\"${if (negate) "!=" else "="}\$1 ", clause.where)
        assertEquals(listOf(Id.featureNumberToOrdinal(number)), clause.argValues)
        assertEquals(listOf(PgType.INT64), clause.argTypes)
        assertNoStoredConversions(clause.where)
      }
    }
  }

  @Test
  fun featureNumberEqualityAcceptsAllIntegralNumberTypes() {
    for (number in listOf<Number>(42.toByte(), 42.toShort(), 42, 42L)) {
      for (negate in listOf(false, true)) {
        val equals = Equals(FeatureNumber, number)
        val clause = buildWhere(if (negate) Not(equals) else equals)
        assertEquals("\"fn\"${if (negate) "!=" else "="}\$1 ", clause.where)
        assertEquals(Id.featureNumberToOrdinal(42L), assertIs<Long>(clause.argValues.single()))
        assertEquals(listOf(PgType.INT64), clause.argTypes)
        assertNoStoredConversions(clause.where)
      }
    }
  }

  @Test
  fun featureNumberMembershipUsesOrdinalArrayAndRawColumn() {
    val numbers = longArrayOf(Long.MIN_VALUE, -1L, 0L, 1L, 128L, 255L, 256L, Long.MAX_VALUE)
    for (negate in listOf(false, true)) {
      val membership = IsAnyOf(FeatureNumber, *numbers.toTypedArray())
      val clause = buildWhere(if (negate) Not(membership) else membership)
      assertEquals("${if (negate) "NOT " else ""}\"fn\"= ANY(\$1) ", clause.where)
      assertContentEquals(LongArray(numbers.size) { Id.featureNumberToOrdinal(numbers[it]) },
        assertIs<LongArray>(clause.argValues.single()))
      assertEquals(listOf(PgType.INT64_ARRAY), clause.argTypes)
      assertNoStoredConversions(clause.where)
    }
  }

  @Test
  fun numericIdEqualityAndMixedMembershipKeepClientSideConversion() {
    val equals = buildWhere(Equals(IdMember, "256"))
    assertEquals("\"fn\"=\$1 ", equals.where)
    assertEquals(listOf(Id.featureNumberToOrdinal(256L)), equals.argValues)
    val membership = buildWhere(IsAnyOf(IdMember, "1", "256", "named-feature"))
    assertTrue(membership.where.startsWith("(\"fn\"=ANY(\$1) OR "))
    assertContentEquals(longArrayOf(Id.featureNumberToOrdinal(1L), Id.featureNumberToOrdinal(256L)),
      assertIs<LongArray>(membership.argValues[0]))
    assertContentEquals(arrayOf("named-feature"), assertIs<Array<String?>>(membership.argValues[1]))
    assertNoStoredConversions(membership.where)
  }

  @Test
  fun logicalRangesUseOnlyNativeByteDecodingAndUnchangedBounds() {
    val expression = PgQueryWhereBuilder.logicalFeatureNumberSql("\"fn\"")
    for (i in 0..7) {
      assertTrue("pg_catalog.get_byte(pg_catalog.int8send(\"fn\" # '-9223372036854775808'::bigint), $i)::bigint << ${i * 8}" in expression)
    }
    assertNoStoredConversions(expression)
    for (number in longArrayOf(Long.MIN_VALUE, -1L, 0L, 128L, 256L, Long.MAX_VALUE)) {
      val operations = listOf(
        Gt(FeatureNumber, number) to ">", Gte(FeatureNumber, number) to ">=",
        Lt(FeatureNumber, number) to "<", Lte(FeatureNumber, number) to "<="
      )
      for ((op, operator) in operations) {
        val clause = buildWhere(op)
        assertEquals("$expression$operator\$1 ", clause.where)
        assertEquals(listOf(number), clause.argValues)
        assertEquals(listOf(PgType.INT64), clause.argTypes)
        assertNoStoredConversions(clause.where)
      }
    }
    for (op in listOf(Gt(IdMember, "256"), Gte(IdMember, "256"), Lt(IdMember, "256"), Lte(IdMember, "256"))) {
      val clause = buildWhere(op)
      assertTrue(clause.where.startsWith(expression))
      assertEquals(listOf(256L), clause.argValues)
      assertNoStoredConversions(clause.where)
    }
  }

  private fun buildWhere(op: Op): PgQueryWhereClause {
    val admin = NakshaAdmin(Id("ordinal_query_database"))
    PgCollection.resolveLayout(admin.collections)
    val collection = NakshaCollection("ordinal_query_features", admin.id)
    collection.databaseId = admin.databaseId
    PgCollection.resolveLayout(collection)
    val tuple = Tuple.encodeObject(collection, admin.collections, version = 1L).tuple
    val physical = PgCollection(Id(admin.id), tuple)
    val request = ReadFeatures().apply { queryMembers = op }
    return assertNotNull(PgQueryWhereBuilder(request, physical).build())
  }

  private fun assertNoStoredConversions(sql: String) {
    assertFalse("ordinal_to_fn" in sql)
    assertFalse("fn_to_ordinal" in sql)
    assertFalse("naksha~admin" in sql)
  }
}
