package naksha.psql

import java.sql.DriverManager
import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.Version
import naksha.base.toOrdinalFeatureNumber
import naksha.model.Naksha
import naksha.model.Tuple
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ErrorResponse
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_CREATE_DB_URL", matches = ".+")
class PgCreateFlowTest {
  @Test
  fun createsCatalogCollectionAndFeatureThroughPublicSessionAndRollsBack() {
    val url = System.getenv("NAKSHA_TEST_CREATE_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "CREATE flow and storage bootstrap must use the same fresh dedicated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh disposable database; existing schemas are never cleared")
        }
      }
    }
    val config = PgConfig("create_flow_test").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val catalog = NakshaCatalog(storage.id, "create_flow_catalog")
    val collection = NakshaCollection("create_flow_features", catalog.id).apply {
      databaseId = storage.id.text
      members = MemberList(Member("name"))
      indices = IndexList()
      shift = 12
    }
    val feature = NakshaFeature("create-flow-feature").apply {
      properties.setRaw("name", "extracted payload")
      setRaw("payload", "unextracted payload")
    }
    storage.newSession(Naksha.useAdminOptions(), false).use { session ->
      try {
        val catalogTuple = create(session, Write().createCatalog(catalog), "catalog CREATE")
        val collectionTuple = create(session, Write().createCollection(collection), "collection CREATE")
        val featureTuple = create(session, Write().createFeature(collection, feature), "feature CREATE")
        val transaction = assertNotNull(session.getTransaction())
        assertEquals(1, transaction.featuresModified)
        assertEquals(storage.id.number, featureTuple.tupleNumber.databaseNumber)
        assertEquals(Id(catalog.id).intValue, featureTuple.tupleNumber.catalogNumber)
        assertEquals(Id(collection.id).intValue, featureTuple.tupleNumber.collectionNumber)
        assertEquals(Id(feature.id).number, featureTuple.tupleNumber.featureNumber)
        assertEquals(catalogTuple.version, collectionTuple.version)
        assertEquals(collectionTuple.version, featureTuple.version)
        val pgCatalog = PgCatalog(catalogTuple)
        val pgCollection = PgCollection(pgCatalog.id, collectionTuple)
        val reserved = assertNotNull(session.getPgCollection(pgCatalog.id.intValue, Id.COLLECTIONS_COL_ID.intValue))
        assertEquals(pgCatalog.id, reserved.catalogId)
        assertEquals(storage.id.number, pgCollection.naksha.databaseNumber,
          "A decoded descriptor must resolve its database number from databaseId when its generic tn is not exposed")

        val temporaryCatalog = NakshaCatalog(storage.id, "create_flow_temporary")
        val invalidCollection = NakshaCollection("create_flow_invalid", catalog.id).apply {
          members = MemberList(Member("name"))
          indices = null
        }
        val failed = session.executeWrite(WriteRequest()
          .add(Write().createCatalog(temporaryCatalog))
          .add(Write().createCollection(invalidCollection)))
        val error = assertIs<ErrorResponse>(failed, "Mixed layout declarations must fail").error
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, error.code)
        assertEquals("Collection members and indices must either both be null or both be explicitly declared", error.msg,
          "Savepoint rollback must preserve the original preparation error")

        // Read physical roots after the failed request; provisional cache rollback remains deferred under db11.
        session.useConnNonNull(false) { conn ->
          assertFalse(conn.autoCommit)
          conn.jdbc.createStatement().use { statement ->
            val schema = PgUtil.quoteLiteral(Id(temporaryCatalog.id).catalogName())
            statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = $schema").use { rows ->
              assertFalse(rows.next(), "Request rollback must remove the prepared temporary schema and its internal tables")
            }
            val root = storage.database.pgAdminCatalogs.qualifiedName
            val ordinal = Id(temporaryCatalog.id).number.toOrdinalFeatureNumber()
            statement.executeQuery("SELECT 1 FROM $root WHERE fn = $ordinal").use { rows ->
              assertFalse(rows.next(), "Failed batch must not persist temporary catalog metadata")
            }
            val invalidRoot = "${pgCatalog.quotedName}.${PgUtil.quoteIdent(Id(invalidCollection.id).collectionName())}"
            statement.executeQuery("SELECT to_regclass(${PgUtil.quoteLiteral(invalidRoot)}) IS NULL").use { rows ->
              assertTrue(rows.next())
              assertTrue(rows.getBoolean(1), "Invalid collection must not leave a physical root")
              assertFalse(rows.next())
            }
          }
          val storedCatalog = read(conn, storage.database.pgAdminCatalogs, catalogTuple)
          assertEquals(catalog.id, storedCatalog.decodeCatalog().id)
          assertEquals(storage.id.text, storedCatalog.decodeCatalog().databaseId)
          val storedCollection = read(conn, reserved, collectionTuple)
          assertEquals(collection.id, storedCollection.decodeCollection().id)
          assertEquals(catalog.id, storedCollection.decodeCollection().catalogId)
          assertEquals(storage.id.text, storedCollection.decodeCollection().databaseId)
          assertEquals(storage.id.number, storedCollection.decodeCollection().databaseNumber)
          val storedFeature = read(conn, pgCollection, featureTuple)
          assertEquals("extracted payload", storedFeature.getString(Member("name")))
          val decoded = storedFeature.decodeFeature(null)
          assertEquals(feature.id, decoded.id)
          assertEquals("extracted payload", decoded.properties.getRaw("name"))
          assertEquals("unextracted payload", decoded.getRaw("payload"))
        }
      } finally {
        // Commit also writes the transaction log; this probe covers transactional CREATE only.
        session.rollback()
      }
    }
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = ${PgUtil.quoteLiteral(Id(catalog.id).catalogName())}").use { rows ->
          assertFalse(rows.next(), "Rollback must remove the catalog schema and its collection roots")
        }
        val root = storage.database.pgAdminCatalogs.qualifiedName
        statement.executeQuery("SELECT 1 FROM $root WHERE fn = ${Id(catalog.id).number.toOrdinalFeatureNumber()}").use { rows ->
          assertFalse(rows.next(), "Rollback must remove the catalog metadata row")
        }
      }
    }
  }

  private fun create(session: PgSession, write: Write, stage: String): Tuple {
    val response = session.executeWrite(WriteRequest().add(write))
    val success = assertIs<SuccessResponse>(response, "$stage failed: $response")
    return assertNotNull(assertNotNull(success.featureTupleList.single()).tuple, "$stage must return its tuple")
  }

  private fun read(conn: PgConnection, collection: PgCollection, expected: Tuple): Tuple {
    val ordinal = expected.tupleNumber.featureNumber.toOrdinalFeatureNumber()
    conn.jdbc.createStatement().use { statement ->
      statement.executeQuery("SELECT fn FROM ${collection.qualifiedName} WHERE fn = $ordinal").use { rows ->
        assertTrue(rows.next())
        assertEquals(ordinal, rows.getLong(1), "Root must store the ordinal feature number")
        assertFalse(rows.next(), "Root must contain exactly one state for the created identifier")
      }
    }
    val rows = PgRows().withCollection(collection)
    conn.execute("SELECT * FROM ${collection.qualifiedName} WHERE fn = $ordinal").fetch().use { cursor ->
      rows.readAll(cursor)
    }
    assertEquals(1, rows.size)
    val actual = assertNotNull(rows.getTuple(0))
    assertEquals(expected.tupleNumber, actual.tupleNumber)
    assertEquals(expected.id, actual.id)
    assertEquals(Action.CREATE, actual.action)
    assertEquals(Version.HEAD.number, actual.nextVersion)
    assertContentEquals(expected.featureBytes, actual.featureBytes)
    return actual
  }
}
