package naksha.diff.jsonpatch

import naksha.base.Platform
import naksha.diff.DifferenceCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


// most of these tests are based on [DifferenceCalculatorTest]
class JsonPatchFactoryTest {

    @Test
    fun shouldReturnEmptyListForNullDiff(){
        assertTrue(JsonPatchFactory.jsonPatch(null).isEmpty())
    }

    @Test
    fun shouldCreateJsonPatchForSimpleMapDiff() {
        // Given:
        val left = Platform.fromJSON(
            """
            {
                "name": "John",
                "age": 37,
                "address": {
                    "city": "London",
                    "street": "Abbey Road",
                    "houseNo": 12
                }
            }
        """.trimIndent()
        )

        // And
        val right = Platform.fromJSON(
            """
            {
                "name": "John",
                "age": 39,
                "address": {
                    "city": "Fordwich",
                    "houseNo": 71
                }
            }
        """.trimIndent()
        )

        // When
        val leftToRightDiff = DifferenceCalculator.calculateDifference(left, right)

        // And
        val jsonPatch = JsonPatchFactory.jsonPatch(leftToRightDiff)

        // And:
        val serializedJsonPatch = Platform.toJSON(jsonPatch)

        // Then
        val expected = """
            [
              {
                "path": "/age",
                "value": 39,
                "op": "replace"
              },
              {
                "path": "/address/city",
                "value": "Fordwich",
                "op": "replace"
              },
              {
                "path": "/address/street",
                "op": "remove"
              },
              {
                "path": "/address/houseNo",
                "value": 71,
                "op": "replace"
              }
            ]
        """.trimIndent()
        assertJsonsAreEqual(expected, serializedJsonPatch)
    }

    @Test
    fun shouldCreateJsonPatchForSimpleListDiff() {
        // Given:
        val shorter = listOf(0, 1, 2)
        val longer = listOf(0, "one", 2, "three")

        // When: diffing from shorter with longer
        val shorterToLongerDiff = DifferenceCalculator.calculateDifference(shorter, longer)

        // And
        val shorterToLongerJsonPatch = JsonPatchFactory.jsonPatch(shorterToLongerDiff)

        // And:
        val serializedShorterToLonger = Platform.toJSON(shorterToLongerJsonPatch)

        // Then
        val expectedShorterToLonger = """
            [
              {
                "path": "/1",
                "value": "one",
                "op": "replace"
              },
              {
                "path": "/3",
                "value": "three",
                "op": "add"
              }
            ]

        """.trimIndent()
        assertJsonsAreEqual(expectedShorterToLonger, serializedShorterToLonger)

        // When: diffing from shorter with longer
        val longerToShorterDiff = DifferenceCalculator.calculateDifference(longer, shorter)

        // And
        val longerToShorterJsonPatch = JsonPatchFactory.jsonPatch(longerToShorterDiff)

        // And:
        val serializedLongerToShorter = Platform.toJSON(longerToShorterJsonPatch)

        // Then
        val expectedLongerToShorter = """
            [
              {
                "path": "/1",
                "value": 1,
                "op": "replace"
              },
              {
                "path": "/3",
                "op": "remove"
              }
            ] 
       """.trimIndent()
        assertJsonsAreEqual(expectedLongerToShorter, serializedLongerToShorter)
    }

    @Test
    fun shouldCreateJsonPatchForComplexMaps(){
        // Given:
        val left = Platform.fromJSON(
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
        val right = Platform.fromJSON(
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

        // And
        val jsonPatch = JsonPatchFactory.jsonPatch(diff)

        // And
        val serializedJsonPatch = Platform.toJSON(jsonPatch)

        // Then
        val expected = """
           [
              {
                "path": "/staff/0/seniority",
                "value": "senior",
                "op": "replace"
              },
              {
                "path": "/staff/0/roles/0/system",
                "value": "s2",
                "op": "replace"
              },
              {
                "path": "/staff/0/roles/1",
                "op": "remove"
              },
              {
                "path": "/staff/1/name",
                "value": "Stan",
                "op": "replace"
              },
              {
                "path": "/staff/1/id",
                "value": 999,
                "op": "replace"
              },
              {
                "path": "/staff/1/seniority",
                "op": "remove"
              },
              {
                "path": "/staff/1/roles/0",
                "op": "remove"
              },
              {
                "path": "/staff/1/roles/1",
                "op": "remove"
              }
            ]
        """.trimIndent()
        assertJsonsAreEqual(expected, serializedJsonPatch)
    }

    private fun assertJsonsAreEqual(expected: String, actual: String) {
        val expectedFlat = Platform.toJSON(Platform.fromJSON(expected))
        val actualFlat = Platform.toJSON(Platform.fromJSON(actual))
        assertEquals(expectedFlat, actualFlat)
    }
}