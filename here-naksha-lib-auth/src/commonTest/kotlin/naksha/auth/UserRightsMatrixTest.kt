package naksha.auth

import naksha.auth.action.ReadFeatures
import naksha.auth.attribute.FeatureAttributes
import kotlin.test.*

class UserRightsMatrixTest {

    @Test
    fun shouldMatchSimpleArm() {
        // Given:
        val urm = UserRightsMatrix()
            .withService(
                "sample_service", ServiceUserRights()
                    .withAction(
                        "readFeatures", UserAction()
                            .withRights(
                                UserRights()
                                    .withPropertyCheck("id", "my-unique-feature-id")
                                    .withPropertyCheck("storageId", "id-with-wildcard-*")
                            )

                    )
            )

        // And:
        val arm = AccessRightsMatrix()
            .withService(
                "sample_service",
                ServiceAccessRights().withAction(
                    ReadFeatures().withAttributes(
                        FeatureAttributes()
                            .id("my-unique-feature-id")
                            .storageId("id-with-wildcard-suffix")
                    )
                )
            )

        // Then:
        assertTrue(urm.matches(arm))
    }

    @Test
    fun shouldMergeWithExistingService() {
        // Given:
        val urm = UserRightsMatrix()

        // And:
        val firstActionName = "some_action"
        val firstService = ServiceUserRights().withAction(
            firstActionName,
            UserAction().withRights(
                UserRights().withPropertyCheck("foo", "bar")
            )
        )

        // And:
        val secondActionName = "other_action"
        val secondService = ServiceUserRights().withAction(
            secondActionName,
            UserAction().withRights(
                UserRights().withPropertyCheck("fuzz", "buzz")
            )
        )

        // When:
        val serviceName = "some_service"
        urm.withService(serviceName, firstService)

        // And:
        urm.withService(serviceName, secondService)

        // When:
        val retrievedService = urm.useService(serviceName)

        // Then:
        retrievedService[firstActionName].let { firstAction ->
            assertNotNull(firstAction)
            assertEquals(1, firstAction.size)
            assertEquals("bar", firstAction[0]!!["foo"])
        }
        retrievedService[secondActionName].let { secondAction ->
            assertNotNull(secondAction)
            assertEquals(1, secondAction.size)
            assertEquals("buzz", secondAction[0]!!["fuzz"])
        }
    }

    @Test
    fun shouldReturnUnregisteredService() {
        // Given: URM without service
        val urm = UserRightsMatrix()

        // When: getting service that was not in URM before
        val initialService = urm.useService("some_service")

        // Then: requested service got created
        assertNotNull(initialService)

        // When: editing requested service
        val actionName = "some_action"
        initialService.withAction(
            actionName,
            UserAction().withRights(
                UserRights()
                    .withPropertyCheck("id", "id_prefix_*")
                    .withPropertyCheck("foo", "bar")
            )

        )

        // And: fetching this service directly from ARM again
        val retrievedService = urm.useService("some_service")

        // Then: returned instance contains modifications
        assertSame(initialService, retrievedService)
        retrievedService[actionName].let { action ->
            assertNotNull(action)
            assertEquals(1, action.size)
            assertEquals("id_prefix_*", action[0]!!["id"])
            assertEquals("bar", action[0]!!["foo"])
        }
    }
}