package naksha.auth

import naksha.auth.check.Equals
import naksha.auth.check.StartsWith
import naksha.auth.naksha.FeatureParams
import naksha.auth.naksha.NakshaOps
import naksha.base.Platform.Platform_C.fromJson
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.objects.NakshaStorage
import kotlin.test.*

class UserRightsMatrixTest {

    @Test
    fun shouldMatchSimpleArm() {
        // Given:
        val urm = fromJson("""{
  "naksha": {
    "readFeatures": [
        {"id": "test_feature", "storageId", "demo-*"}
    ]
  }
}""", UserRightsMatrix.TYPE)
        assertNotNull(urm).apply {
            assertIs<UserRights>(this["naksha"]).apply {
                assertIs<UserRightsFilterList>(this["readFeatures"]).apply {
                    assertIs<UserRightsFilter>(this[0]).apply {
                        assertIs<Equals>(this["id"]).apply {
                            assertNull(this.allOf)
                            assertNotNull(this.anyOf).apply {
                                assertEquals(1, this.size)
                                assertEquals("test_feature", this[0])
                            }
                        }
                        assertIs<StartsWith>(this["storageId"]).apply {
                            assertNull(this.allOf)
                            assertNotNull(this.anyOf).apply {
                                assertEquals(1, this.size)
                                assertEquals("demo-", this[0])
                            }
                        }
                    }
                }
            }
        }

        // And:
        val storage = NakshaStorage("demo-foo", "com.foo.some.Demo")
        val map = NakshaMap("test_map")
        val col = NakshaCollection("test_collection", map.id)
        val feature = NakshaFeature("test_feature")
        val ops = NakshaOps()
        ops.readFeatures += FeatureParams(feature, col, map, storage)

        // Then:
        assertTrue(urm.matches(ops))
    }

//    @Test
//    fun shouldMergeWithExistingService() {
//        // Given:
//        val urm = UserRightsMatrix()
//
//        // And:
//        val firstActionName = "some_action"
//        val firstService = ServiceUserRights().withAction(
//            firstActionName,
//            UserAction().withRights(
//                UserRights().withPropertyCheck("foo", "bar")
//            )
//        )
//
//        // And:
//        val secondActionName = "other_action"
//        val secondService = ServiceUserRights().withAction(
//            secondActionName,
//            UserAction().withRights(
//                UserRights().withPropertyCheck("fuzz", "buzz")
//            )
//        )
//
//        // When:
//        val serviceName = "some_service"
//        urm.withService(serviceName, firstService)
//
//        // And:
//        urm.withService(serviceName, secondService)
//
//        // When:
//        val retrievedService = urm.useService(serviceName)
//
//        // Then:
//        retrievedService[firstActionName].let { firstAction ->
//            assertNotNull(firstAction)
//            assertEquals(1, firstAction.size)
//            assertEquals("bar", firstAction[0]!!["foo"])
//        }
//        retrievedService[secondActionName].let { secondAction ->
//            assertNotNull(secondAction)
//            assertEquals(1, secondAction.size)
//            assertEquals("buzz", secondAction[0]!!["fuzz"])
//        }
//    }
//
//    @Test
//    fun shouldReturnUnregisteredService() {
//        // Given: URM without service
//        val urm = UserRightsMatrix()
//
//        // When: getting service that was not in URM before
//        val initialService = urm.useService("some_service")
//
//        // Then: requested service got created
//        assertNotNull(initialService)
//
//        // When: editing requested service
//        val actionName = "some_action"
//        initialService.withAction(
//            actionName,
//            UserAction().withRights(
//                UserRights()
//                    .withPropertyCheck("id", "id_prefix_*")
//                    .withPropertyCheck("foo", "bar")
//            )
//
//        )
//
//        // And: fetching this service directly from ARM again
//        val retrievedService = urm.useService("some_service")
//
//        // Then: returned instance contains modifications
//        assertSame(initialService, retrievedService)
//        retrievedService[actionName].let { action ->
//            assertNotNull(action)
//            assertEquals(1, action.size)
//            assertEquals("id_prefix_*", action[0]!!["id"])
//            assertEquals("bar", action[0]!!["foo"])
//        }
//    }
}