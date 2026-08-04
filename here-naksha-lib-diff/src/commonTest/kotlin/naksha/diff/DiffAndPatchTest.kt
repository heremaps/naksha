package naksha.diff

import naksha.base.Base
import kotlin.test.Test
import kotlin.test.assertEquals

class DiffAndPatchTest {

    @Test
    fun diffingAndPatchingShouldResultInOriginalObject() {
        // Given:
        val left = Base.fromJSON(
            """
            {
                "company": "Abc",
                "staff": [
                    {
                        "name": "John",
                        "id": 123,
                        "seniority": "junior",
                        "roles": [
                            {
                                "system": "s1",
                                "role": "admin"
                            },
                            {
                                "system": "s2",
                                "role": "user"
                            }
                        ]
                    },
                    {
                        "name": "Phil",
                        "id": 456,
                        "seniority": "senior",
                        "roles": [
                            {
                                "system": "s1",
                                "role": "admin"
                            },
                            {
                                "system": "s2",
                                "role": "admin"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        )

        // And
        val right = Base.fromJSON(
            """
            {
                "company": "Abc",
                "staff": [
                    {
                        "name": "John",
                        "id": 123,
                        "seniority": "senior",
                        "roles": [
                            {
                                "system": "s2",
                                "role": "admin"
                            }
                        ]
                    },
                    {
                        "name": "Stan",
                        "id": 999,
                        "roles": []
                    }
                ]
            }
        """.trimIndent()
        )

        // When
        val diff = DifferenceCalculator.calculateDifference(left, right)

        // And:
        Patcher.patch(left!!, diff)

        // Then:
        val leftJson = Base.toJSON(left)
        val rightJson = Base.toJSON(right)
        assertEquals(leftJson, rightJson)
    }
}
