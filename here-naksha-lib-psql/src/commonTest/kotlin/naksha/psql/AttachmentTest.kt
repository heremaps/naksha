package naksha.psql

import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import kotlin.test.*

class AttachmentTest : PgTestBase() {

    @Test
    fun insertFeatureWithAttachment() {
        val attachmentOriginal = "this is a test"
        val attachmentBytes = attachmentOriginal.encodeToByteArray()
        val featureToCreate = randomFeature()
        val xyz = featureToCreate.properties.xyz
        xyz.tags.clear()
        xyz.tags.addTag("wicked", false)

        // Write the feature
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection.mapId, collection.id, featureToCreate).withAttachment(attachmentBytes))
        }
        executeWrite(writeFeaturesReq).apply {
            // Verify the result (will come from cache)
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals(Action.CREATED, feature.properties.xyz.guid?.tupleNumber?.action)

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)
        }

        // Read the feature
        Naksha.cache.clear()
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += featureToCreate.id
        }).apply {
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals(Action.CREATED, feature.properties.xyz.guid?.tupleNumber?.action)

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)
        }
    }

    @Test
    fun upsertFeatureWithAttachment() {
        val attachmentOriginal = "this is a test"
        val attachmentBytes = attachmentOriginal.encodeToByteArray()
        val featureId = PlatformUtil.randomString()
        val featureToCreate = randomFeature(featureId)
        featureToCreate.properties["test"] = "start"
        val xyz = featureToCreate.properties.xyz
        xyz.tags.clear()
        xyz.tags.addTag("wicked", false)

        // Write the feature
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().upsertFeature(collection.mapId, collection.id, featureToCreate).withAttachment(attachmentBytes))
        }
        executeWrite(writeFeaturesReq).apply {
            // Verify the result (will come from cache)
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals("start", feature.properties["test"])
            assertEquals(Action.CREATED, feature.properties.xyz.guid?.tupleNumber?.action)

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)
        }

        // Read the feature
        Naksha.cache.clear()
        val readFeature: NakshaFeature
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += featureToCreate.id
        }).apply {
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals("start", feature.properties["test"])
            assertEquals(Action.CREATED, feature.properties.xyz.guid?.tupleNumber?.action)

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)

            readFeature = feature
        }
        val insertedFeatureGuid = readFeature.properties.xyz.guid
        assertNotNull(insertedFeatureGuid)
        assertEquals(featureId, insertedFeatureGuid.id)
        assertEquals(storage.number, insertedFeatureGuid.tupleNumber.storageNumber)
        assertEquals(map.number, insertedFeatureGuid.tupleNumber.mapNumber)
        assertEquals(collection.number, insertedFeatureGuid.tupleNumber.collectionNumber)

        // Now, update the feature, leave the attachment as it is.
        // In other words, for this upsert, we do not provide an attachment, but expect to find it in the response!
        Naksha.cache.clear()
        val upsertFeature: NakshaFeature = readFeature.copy(true)
        upsertFeature.properties["test"] = "end"
        val updateFeatureReq = WriteRequest().apply {
            // We do not modify attachment, therefore it should be UNDEFINED
            add(Write().upsertFeature(collection.mapId, collection.id, upsertFeature))
        }
        executeWrite(updateFeatureReq).apply {
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals("end", feature.properties["test"])
            assertEquals(Action.UPDATED, feature.properties.xyz.guid?.tupleNumber?.action)

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)
        }
    }

    @Test
    fun updateFeatureWithAttachment() {
        val attachmentOriginal = "this is a test"
        val attachmentBytes = attachmentOriginal.encodeToByteArray()
        val featureId = PlatformUtil.randomString()
        val featureToCreate = randomFeature(featureId)
        featureToCreate.properties["test"] = "start"
        val xyz = featureToCreate.properties.xyz
        xyz.tags.clear()
        xyz.tags.addTag("wicked", false)

        // Write the feature
        val writeFeaturesReq = WriteRequest().apply {
            add(Write().createFeature(collection.mapId, collection.id, featureToCreate).withAttachment(attachmentBytes))
        }
        executeWrite(writeFeaturesReq).apply {
            // Verify the result (will come from cache)
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals(Action.CREATED, feature.properties.xyz.guid?.tupleNumber?.action)
            assertEquals("start", feature.properties["test"])

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)
        }

        // Read the feature
        Naksha.cache.clear()
        val readFeature: NakshaFeature
        executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += featureToCreate.id
        }).apply {
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals(Action.CREATED, feature.properties.xyz.guid?.tupleNumber?.action)
            assertEquals("start", feature.properties["test"])

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)

            readFeature = feature
        }
        val insertedFeatureGuid = readFeature.properties.xyz.guid
        assertNotNull(insertedFeatureGuid)
        assertEquals(featureId, insertedFeatureGuid.id)
        assertEquals(storage.number, insertedFeatureGuid.tupleNumber.storageNumber)
        assertEquals(map.number, insertedFeatureGuid.tupleNumber.mapNumber)
        assertEquals(collection.number, insertedFeatureGuid.tupleNumber.collectionNumber)

        // Now, update the feature, leave the attachment as it is.
        // In other words, for this upsert, we do not provide an attachment, but expect to find it in the response!
        Naksha.cache.clear()
        val updateFeature: NakshaFeature = readFeature.copy(true)
        updateFeature.properties["test"] = "end"
        val updateFeatureReq = WriteRequest().apply {
            // We do not modify attachment, therefore it should be UNDEFINED
            add(Write().updateFeature(collection, updateFeature, true))
        }
        executeWrite(updateFeatureReq).apply {
            assertEquals(1, length)
            assertEquals(1, features.size)
            val feature = assertNotNull(features.first())
            assertEquals(featureToCreate.id, feature.id)
            assertEquals("end", feature.properties["test"])
            assertEquals(Action.UPDATED, feature.properties.xyz.guid?.tupleNumber?.action)

            val featureTupleList = this.featureTupleList
            assertEquals(1, featureTupleList.size)
            val featureTuple = assertNotNull(featureTupleList[0])
            val tuple = assertNotNull(featureTuple.tuple)
            assertNotNull(tuple.attachment)
            assertContentEquals(attachmentBytes, tuple.attachment)
        }
    }
}
