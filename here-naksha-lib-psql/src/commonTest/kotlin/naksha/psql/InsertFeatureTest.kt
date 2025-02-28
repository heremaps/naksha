package naksha.psql

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformUtil
import naksha.geo.SpBoundingBox
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.model.request.query.SpIntersects
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeatures
import kotlin.test.*

class InsertFeatureTest : PgTestBase(NakshaCollection("insert_feature_test_c", TEST_MAP_ID)) {

    @AfterTest
    fun cleanUp() {
        //dropCollection()
    }

    @Test
    fun shouldInsertSingleFeature() {
        // Given: features to create
        val featureToCreate = generateRandomFeature()
        val xyz = featureToCreate.properties.xyz
        xyz.tags.clear()
        xyz.tags.addTag("wicked", false)
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection.mapId, collection.id, featureToCreate))
        }

        // When: executing feature write request
        executeWrite(writeFeaturesReq)

        // And: reading all features from collection
        val readResponse = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
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
                            .hasProperty("author", PgTest.TEST_APP_AUTHOR!!)
                            .hasProperty("action", Action.CREATED.text)
                    }
                    .hasTags(TagList("wicked"))
            }
    }

    @Test
    fun shouldInsertManyFeatures() {
        val count = 1 * 1000
        // Given: features to create
        val featuresToCreate = generateRandomFeatures(count = count)
        val writeFeaturesReq = WriteRequest().apply {
            featuresToCreate.forEach { featureToCreate ->
                add(Write().createFeature(collection.mapId, collection.id, featureToCreate))
            }
        }
        val firstFeatureToCreate = featuresToCreate[0]

        // When: executing feature write request
        val start = Platform.currentNanos()
        val version: Version
        env.storage.newWriteSession(null).use { session ->
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
            collectionIds += collection.id
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
        assertEquals(env.storage.number, firstFeature.storageNumber)
        assertEquals(map.number, firstFeature.mapNumber)
        assertEquals(collection.number, firstFeature.collectionNumber)
        Platform.logger.info("Storage reported guid '${firstFeature.guid}' for first feature")
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
                                .hasProperty("author", PgTest.TEST_APP_AUTHOR!!)
                                .hasProperty("action", Action.CREATED.text)
                        }
                }
        }

        // Read only one feature by ID, bypassing the cache.
        Platform.logger.info("Clear cache and reload feature from database")
        Naksha.cache.clear(env.storage)
        val featuresByIdResponse = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds.add(firstFeatureToCreate.id)
        })

        // Expect it to have the same tuple-number as the first feature originally returned!
        assertEquals(1, featuresByIdResponse.size)
        val binary = featuresByIdResponse.tupleNumberBinary
        assertNotNull(binary)
        assertEquals(1, binary.size)
        val tupleNumber = binary.first()
        assertNotNull(tupleNumber)
        Platform.logger.info("Expect that the originally returned tuple-number is the same as the one from search")
        assertEquals(firstFeature.tupleNumber, tupleNumber)

        // This will force the cache to contact the storage, and to load the tuple.
        val features = featuresByIdResponse.features
        assertEquals(firstFeatureToCreate.id, features[0]!!.id)

        // Read only one feature by bounding box.
        val featuresByBBox = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            query.spatial =
                SpIntersects(SpBoundingBox(firstFeatureToCreate.geometry).addMargin(0.0000001).toPolygon())
        })
        assertEquals(1, featuresByBBox.features.size)
        assertEquals(firstFeatureToCreate.id, featuresByBBox.features[0]!!.id)
    }

    @Test
    fun shouldNotAllowDuplicatedId() {
        // Given
        val originalFeature = generateRandomFeature()
        val featureWithDuplicatedId = generateRandomFeature().apply { id = originalFeature.id }

        // When
        insertFeature(originalFeature)

        // And
        val writeReq = WriteRequest().add(
            Write().createFeature(collection.mapId, collection.id, featureWithDuplicatedId)
        )
        val insertDuplicateResponse = env.storage.newWriteSession().use { session ->
            session.execute(writeReq)
        }

        // Then
        assertIs<ErrorResponse>(insertDuplicateResponse)
        assertEquals(NakshaError.CONFLICT, insertDuplicateResponse.error.code)
    }
}
