package naksha.auth.action

import naksha.base.Platform
import naksha.base.PlatformType
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties
import kotlin.test.assertIs

class ActionByNameTest {

    @Test
    fun shouldIncludeAllSubclasses() {
        // Given: all subclasses of AccessRightsAction
        val typesByName = AccessRightsAction::class.sealedSubclasses
            .mapNotNull { subtype -> subtype.companionObject?.let { subtype to it } }
            .associate { (subtype, companion) ->
                val name = companion.memberProperties
                    .first { it.name == EXPECTED_ACTION_FIELD_NAME }
                    .call(companion)
                name to subtype
            }

        // Then
        typesByName.forEach { (name, kClass) ->
            val type = assertIs<PlatformType<AccessRightsAction<*, *>>>(Platform.forKClass(kClass))
            assertSame(type, ACTIONS_BY_NAME[name], "Unexpected action type for '$name'")
        }
    }

    companion object {
        const val EXPECTED_ACTION_FIELD_NAME = "NAME"
    }
}