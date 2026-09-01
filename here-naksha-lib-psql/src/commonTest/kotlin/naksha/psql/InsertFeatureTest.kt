package naksha.psql

import naksha.base.Action
import naksha.base.Int64
import naksha.base.NakshaError
import naksha.base.Platform
import naksha.base.Version
import naksha.geo.SpBoundingBox
import naksha.model.*
import naksha.model.request.*
import naksha.model.request.query.SpIntersects
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaFeature
import kotlin.test.*

class InsertFeatureTest : PgTestBase() {

    @Test
    fun shouldInsertSingleFeature() {
        // Given: features to create
        val featureToCreate = randomFeature()
        val xyz = featureToCreate.properties.xyz
        xyz.tags.clear()
        xyz.tags.addTag("wicked", false)
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection.catalogId, collection.id, featureToCreate))
        }

        // When: executing feature write request
        executeWrite(writeFeaturesReq)

        // And: reading all features from collection
        val readResponse = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += featureToCreate.id
        })
        val retrievedFeatures = readResponse.features

        // Then: we got 1 feature
        assertEquals(1, retrievedFeatures.size)

        // And:
        val retrievedFeature = retrievedFeatures.find { it?.id == featureToCreate.id }
        assertNotNull(retrievedFeature, "Missing feature with id: ${featureToCreate.id}")
        assertThatFeature(retrievedFeature)
            .isIdenticalTo(
                other = featureToCreate,
                ignoreProps = true // we ignore properties because Xyz is not defined by client
            )
            .hasPropertiesThat { retrievedProperties ->
                retrievedProperties
                    .hasFeatureType(featureToCreate.properties.featureType)
                    .hasXyzThat { retrievedXyz ->
                        retrievedXyz
                            .hasProperty("appId", PgTest.TEST_APP_ID)
                            .hasProperty("author", PgTest.TEST_APP_AUTHOR)
                            .hasProperty("action", Action.CREATE.text)
                    }
                    .hasTags(TagList("wicked"))
            }
    }

    @Test
    fun insertFeatureWithNumericId() {
        val featureNumber = 58626681L
        val json = """{
  "type": "Feature",
  "id": "$featureNumber",
  "geometry": {
    "type": "LineString",
    "bbox": null,
    "coordinates": [
      [
        21.00856,
        52.2325,
        146.68
      ],
      [
        21.00879,
        52.23255,
        145.78
      ],
      [
        21.00897,
        52.23258,
        144.84
      ]
    ]
  },
  "properties": {
     "name": "Test"
  }
}"""
        // Given: features to create
        val featureToCreate = NakshaFeature.fromJson(json)
        val xyz = featureToCreate.properties.xyz
        xyz.tags.addTag("wicked", false)
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection.catalogId, collection.id, featureToCreate))
        }

        // When: executing feature write request
        executeWrite(writeFeaturesReq)

        // And: reading all features from collection
        val readResponse = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += featureToCreate.id
        })
        val retrievedFeatures = readResponse.features

        // Then: we got 1 feature
        assertEquals(1, retrievedFeatures.size)

        // And:
        val retrievedFeature = retrievedFeatures.find { it?.id == featureToCreate.id }
        assertNotNull(retrievedFeature, "Missing feature with id: ${featureToCreate.id}")
        assertEquals(Int64(featureNumber), retrievedFeature.properties.xyz.guid?.tupleNumber?.featureNumber)
        assertEquals(Int64(featureNumber), retrievedFeature.properties.xyz.guid?.tupleNumber?.featureNumber)
        assertThatFeature(retrievedFeature)
            .isIdenticalTo(
                other = featureToCreate,
                ignoreProps = true // we ignore properties because Xyz is not defined by client
            )
            .hasPropertiesThat { retrievedProperties ->
                retrievedProperties
                    .hasXyzThat { retrievedXyz ->
                        retrievedXyz
                            .hasProperty("appId", PgTest.TEST_APP_ID)
                            .hasProperty("author", PgTest.TEST_APP_AUTHOR)
                            .hasProperty("action", Action.CREATE.text)
                    }
                    .hasTags(TagList("wicked"))
            }
    }

    @Test
    fun insertFeatureAndEnsureDefaultEncoding() {
        // Given: features to create
        val featureToCreate = randomFeature()
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection.catalogId, collection.id, featureToCreate))
        }

        // When: executing feature write request
        executeWrite(writeFeaturesReq)

        // And: reading all features from collection
        val readResponse = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += featureToCreate.id
        })
        val retrievedFeatures = readResponse.features

        // Then: we got 1 feature
        assertEquals(1, retrievedFeatures.size)

        // And:
        val retrievedFeature = retrievedFeatures.find { it?.id == featureToCreate.id }
        assertNotNull(retrievedFeature, "Missing feature with id: ${featureToCreate.id}")
    }

    @Test
    fun shouldInsertManyFeatures() {
        val count = 500
        // Given: features to create
        val featuresToCreate = randomFeatures(count = count)
        val writeFeaturesReq = WriteRequest().apply {
            featuresToCreate.forEach { featureToCreate ->
                add(Write().createFeature(collection.catalogId, collection.id, featureToCreate))
            }
        }
        val firstFeatureToCreate = featuresToCreate[0]

        // When: executing feature write request
        val start = Platform.currentNanos()
        val version: Version
        newWriteSession().use { session ->
            version = session.useTransaction().version
            val response = assertSuccess(session.execute(writeFeaturesReq))
            session.commit()
            response
        }
        val end = Platform.currentNanos()
        val time = end - start
        val totalMillis = time / 1_000_000
        val featuresPerSecond = count.toDouble() / (time.toDouble()/(1_000_000_000.toDouble()))
        Platform.logger.info("Insert took ${totalMillis}ms, $featuresPerSecond features per second")
        Platform.logger.info("Inserted version: $version")
        assertEquals(count, featuresToCreate.size)

        // And: reading all features from collection
        val readResponse = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
//            this.version = version
//            this.minVersion = version
        })
        val retrievedFeatures = readResponse.features

        // Then: we got <count> features
        //assertEquals(count, retrievedFeatures.size)
        assertTrue(count <= retrievedFeatures.size)

        // And:
        val firstFeature = retrievedFeatures.find { it?.id == firstFeatureToCreate.id }
        assertNotNull(firstFeature)
        assertEquals(storage.number, firstFeature.properties.xyz.guid?.tupleNumber?.databaseNumber)
        assertEquals(catalog.catalogNumber, firstFeature.properties.xyz.guid?.tupleNumber?.catalogNumber)
        assertEquals(collection.collectionNumber, firstFeature.properties.xyz.guid?.tupleNumber?.collectionNumber)
        Platform.logger.info("Storage reported guid '${firstFeature.properties.xyz.guid}' for first feature")
        assertEquals(firstFeatureToCreate.id, firstFeature.id)

        // And:
        featuresToCreate.forEach { featureToCreate ->
            val retrievedFeature = retrievedFeatures.find { it?.id == featureToCreate.id }
            assertNotNull(retrievedFeature, "Missing feature with id: ${featureToCreate.id}")
            assertThatFeature(retrievedFeature)
                .isIdenticalTo(
                    other = featureToCreate,
                    ignoreProps = true // we ignore properties because Xyz is not defined by client
                )
                .hasPropertiesThat { retrievedProperties ->
                    retrievedProperties
                        .hasFeatureType(featureToCreate.properties.featureType)
                        .hasXyzThat { retrievedXyz ->
                            retrievedXyz
                                .hasProperty("appId", PgTest.TEST_APP_ID)
                                .hasProperty("author", PgTest.TEST_APP_AUTHOR)
                                .hasProperty("action", Action.CREATE.text)
                        }
                }
        }

        // Read only one feature by ID, bypassing the cache.
        Platform.logger.info("Clear cache and reload feature from database")
        Naksha.cache.clear(storage)
        val featuresByIdResponse = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds.add(firstFeatureToCreate.id)
        })

        // Expect it to have the same tuple-number as the first feature originally returned!
        assertEquals(1, featuresByIdResponse.length)
        val tuples = featuresByIdResponse.featureTupleList
        assertEquals(1, tuples.size)
        val tupleNumber = assertNotNull(tuples.first()).tupleNumber
        assertNotNull(tupleNumber)
        Platform.logger.info("Expect that the originally returned tuple-number is the same as the one from search")
        assertEquals(firstFeature.properties.xyz.guid?.tupleNumber, tupleNumber)

        // This will force the cache to contact the storage, and to load the tuple.
        val features = featuresByIdResponse.features
        assertEquals(firstFeatureToCreate.id, features[0]!!.id)

        // Read only one feature by bounding box.
        val featuresByBBox = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            query.spatial =
                SpIntersects(SpBoundingBox(firstFeatureToCreate.geometry).addMargin(0.0000001).toPolygon())
        })
        assertEquals(1, featuresByBBox.features.size)
        assertEquals(firstFeatureToCreate.id, featuresByBBox.features[0]!!.id)
    }

    @Test
    fun readByMixedNumericAndNamedIds() {
        // Given: one numeric-ID feature and one named-ID feature
        val numericId = "99887766"
        val numericJson = """{"type":"Feature","id":"$numericId","geometry":null,"properties":{}}"""
        val numericFeature = NakshaFeature.fromJson(numericJson)

        val namedFeature = randomFeature() // has a UUID-style named id (fn < 0)

        val writeReq = WriteRequest().apply {
            add(Write().createFeature(collection.catalogId, collection.id, numericFeature))
            add(Write().createFeature(collection.catalogId, collection.id, namedFeature))
        }
        executeWrite(writeReq)

        // When: reading both features in a single request with mixed IDs
        val readResponse = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += numericId
            featureIds += namedFeature.id
        })
        val retrieved = readResponse.features

        // Then: both features are returned
        assertEquals(2, retrieved.size)
        assertNotNull(retrieved.find { it?.id == numericId }, "Missing numeric-ID feature '$numericId'")
        assertNotNull(retrieved.find { it?.id == namedFeature.id }, "Missing named-ID feature '${namedFeature.id}'")
    }

    @Test
    fun shouldNotAllowDuplicatedId() {
        // Given
        val originalFeature = randomFeature()
        val featureWithDuplicatedId = randomFeature().apply { id = originalFeature.id }

        // When
        insertFeature(originalFeature)

        // And
        val writeReq = WriteRequest().add(
            Write().createFeature(collection.catalogId, collection.id, featureWithDuplicatedId)
        )
        val insertDuplicateResponse = newWriteSession().use { session ->
            session.execute(writeReq)
        }

        // Then
        assertIs<ErrorResponse>(insertDuplicateResponse)
        assertEquals(NakshaError.CONFLICT, insertDuplicateResponse.error.code)
    }
}
