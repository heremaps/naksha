package naksha.diff

import naksha.base.Platform
import naksha.diff.DifferenceCalculator.DifferenceCalculator_C.calculateDifference
import kotlin.test.*

class DifferenceCalculatorTest {

    @Test
    fun x(){
        val objectToPatch = Platform.fromJSON("""
            {
                "foo": "bar",
                "lorem": "ipsum"
            }
        """.trimIndent())!!

        val diff = MapDiff()
        diff["foo"] = UpdateOp(oldValue = "bar", newValue = "new_bar")
        diff["lorem"] = RemoveOp(oldValue = "ipsum")
        diff["new_field"] = InsertOp(newValue = 123)

        Patcher.patch(objectToPatch, diff)

        val x = Platform.toJSON(objectToPatch)
    }

    @Test
    fun shouldTreatMissingSourceAsInsertion() {
        // Given
        val target = "some_value"

        // When
        val diff = calculateDifference(source = null, target = target)

        // Then
        assertIs<InsertOp>(diff)
        assertEquals(target, diff.newValue)
    }

    @Test
    fun shouldTreatMissingTargetAsRemoval() {
        // Given
        val source = 123

        // When
        val diff = calculateDifference(source = source, target = null)

        // Then
        assertIs<RemoveOp>(diff)
        assertEquals(source, diff.oldValue)
    }

    @Test
    fun shouldCalculateDiffForSimpleMaps() {
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
        val leftToRightDiff = calculateDifference(left, right)

        // Then
        assertNotNull(leftToRightDiff)
        assertIs<MapDiff>(leftToRightDiff)
        assertTrue(leftToRightDiff.keys.containsAll(setOf("age", "address")))
        assertEquals(2, leftToRightDiff.size)
        assertEquals(UpdateOp(37, 39), leftToRightDiff["age"])
        val leftToRightAddressChange = leftToRightDiff["address"]
        assertIs<MapDiff>(leftToRightAddressChange)
        assertEquals(UpdateOp("London", "Fordwich"), leftToRightAddressChange["city"])
        assertEquals(RemoveOp("Abbey Road"), leftToRightAddressChange["street"])
        assertEquals(UpdateOp(12, 71), leftToRightAddressChange["houseNo"])

        // When
        val rightToLeftDiff = calculateDifference(right, left)

        // Then
        assertNotNull(rightToLeftDiff)
        assertIs<MapDiff>(rightToLeftDiff)
        assertTrue(rightToLeftDiff.keys.containsAll(setOf("age", "address")))
        assertEquals(2, rightToLeftDiff.size)
        assertEquals(UpdateOp(39, 37), rightToLeftDiff["age"])
        val rightToLeftAddressChange = rightToLeftDiff["address"]
        assertIs<MapDiff>(rightToLeftAddressChange)
        assertEquals(UpdateOp("Fordwich", "London"), rightToLeftAddressChange["city"])
        assertEquals(InsertOp("Abbey Road"), rightToLeftAddressChange["street"])
        assertEquals(UpdateOp(71, 12), rightToLeftAddressChange["houseNo"])
    }

    @Test
    fun shouldCalculateDiffForSimpleLists() {
        // Given:
        val shorter = listOf(0, 1, 2)
        val longer = listOf(0, "one", 2, "three")

        // When: diffing from shorter with longer
        val shorterToLongerDiff = calculateDifference(shorter, longer)

        // Then
        assertNotNull(shorterToLongerDiff)
        assertIs<ListDiff>(shorterToLongerDiff)
        assertEquals(4, shorterToLongerDiff.size)
        assertEquals(null, shorterToLongerDiff[0]) // no diff -> null
        assertEquals(UpdateOp(1, "one"), shorterToLongerDiff[1])
        assertEquals(null, shorterToLongerDiff[2]) // no diff -> null
        assertEquals(InsertOp("three"), shorterToLongerDiff[3])

        // When: diffing longer with shorter (reverse-diff of the previous operation)
        val longerToShorterDiff = calculateDifference(longer, shorter)

        // Then
        assertNotNull(longerToShorterDiff)
        assertIs<ListDiff>(longerToShorterDiff)
        assertEquals(4, longerToShorterDiff.size)
        assertEquals(null, longerToShorterDiff[0]) // no diff -> null
        assertEquals(UpdateOp("one", 1), longerToShorterDiff[1])
        assertEquals(null, longerToShorterDiff[2]) // no diff -> null
        assertEquals(RemoveOp("three"), longerToShorterDiff[3])
    }

    @Test
    fun shouldTreatLogicallySameNumbersAsEqual() {
        // null == no difference
        assertNull(calculateDifference(12.34, 12.34f))
        assertNull(calculateDifference(12.34f, 12.34))
        assertNull(calculateDifference(567, 567L))
        assertNull(calculateDifference(567L, 567))
    }

    @Test
    fun shouldReturnNoDiffForEqualValues() {
        // null == no difference
        assertNull(calculateDifference(source = true, target = true))
        assertNull(calculateDifference(source = "This", target = "This"))
        assertNull(
            calculateDifference(
                source = listOf(1, "two"),
                target = listOf(1, "two")
            )
        )
        assertNull(
            calculateDifference(
                mapOf(
                    "foo" to 1,
                    "bar" to mapOf(
                        "lorem" to "ipsum"
                    )
                ),
                mapOf(
                    "foo" to 1,
                    "bar" to mapOf(
                        "lorem" to "ipsum"
                    )
                )
            )
        )
    }

    @Test
    fun shouldIgnoreSpecifiedKeys() {
        // Given:
        val left = Platform.fromJSON("""
            {
                "foo": "abc",
                "bar": 123
            }
        """.trimIndent())

        // And:
        val right = Platform.fromJSON("""
            {
                "foo": "abc",
                "bar": 456
            }
        """.trimIndent())

        // And:
        val diffContext = object:DiffContext {
            override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean =
                key == "bar"

            override fun areTwoNumbersEqual(first: Number, second: Number): Boolean =
                DiffContext.Default.areTwoNumbersEqual(first, second)
        }

        // When:
        val diffWithIgnore = calculateDifference(left, right, diffContext)

        // Then: foo remained the same, bar is ignored -> there should be no diff
        assertNull(diffWithIgnore)

        // When: running the same diff without ignoring results in some diff
        val diffWithoutIgnore = calculateDifference(left, right)

        // Then
        assertIs<MapDiff>(diffWithoutIgnore)
        assertEquals(UpdateOp(123, 456), diffWithoutIgnore["bar"])
    }

    @Test
    fun shouldCalculateDiffForComplexMaps() {
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
        val diff = calculateDifference(left, right)

        // Then:
        assertNotNull(diff)
        assertIs<MapDiff>(diff)
        val staffDiff = diff["staff"]
        assertIs<ListDiff>(staffDiff)
        val firstEmployee = staffDiff[0]
        assertIs<MapDiff>(firstEmployee)
        assertEquals(UpdateOp("junior", "senior"), firstEmployee["seniority"])
        val firstEmployeeRoles = firstEmployee["roles"]
        assertIs<ListDiff>(firstEmployeeRoles)
        val firstRoleOfFirstEmployee = firstEmployeeRoles[0]
        assertIs<MapDiff>(firstRoleOfFirstEmployee)
        assertEquals(UpdateOp("s1", "s2"), firstRoleOfFirstEmployee["system"])
        val secondRoleOfFirstEmployee = firstEmployeeRoles[1]
        assertIs<RemoveOp>(secondRoleOfFirstEmployee)
        val removedRoleOfFirstEmployee = secondRoleOfFirstEmployee.oldValue
        assertIs<Map<*, *>>(removedRoleOfFirstEmployee)
        assertEquals("s2", removedRoleOfFirstEmployee["system"])
        assertEquals("user", removedRoleOfFirstEmployee["role"])
        val secondEmployee = staffDiff[1]
        assertIs<MapDiff>(secondEmployee)
        assertEquals(UpdateOp("Phil", "Stan"), secondEmployee["name"])
        assertEquals(UpdateOp(456, 999), secondEmployee["id"])
        assertEquals(RemoveOp("senior"), secondEmployee["seniority"])
        val secondEmployeeRoles = secondEmployee["roles"]
        assertIs<ListDiff>(secondEmployeeRoles)
        val firstRoleOfSecondEmployeeOp = secondEmployeeRoles[0]
        assertIs<RemoveOp>(firstRoleOfSecondEmployeeOp)
        val firstRemovedRoleOfSecondEmployee = firstRoleOfSecondEmployeeOp.oldValue
        assertIs<Map<*, *>>(firstRemovedRoleOfSecondEmployee)
        assertEquals("s1", firstRemovedRoleOfSecondEmployee["system"])
        assertEquals("admin", firstRemovedRoleOfSecondEmployee["role"])
        val secondRoleOfSecondEmployeeOp = secondEmployeeRoles[1]
        assertIs<RemoveOp>(secondRoleOfSecondEmployeeOp)
        val secondRemovedRoleOfSecondEmployee = secondRoleOfSecondEmployeeOp.oldValue
        assertIs<Map<*, *>>(secondRemovedRoleOfSecondEmployee)
        assertEquals("s2", secondRemovedRoleOfSecondEmployee["system"])
        assertEquals("admin", secondRemovedRoleOfSecondEmployee["role"])
    }

    @Test
    fun shouldCalculateDiffForSimpleArrays() {
        // Given:
        val shorter = arrayOf(0, 1, 2)
        val longer = arrayOf(0, "one", 2, "three")

        // When: diffing from shorter with longer
        val shorterToLongerDiff = calculateDifference(shorter, longer)

        // Then
        assertNotNull(shorterToLongerDiff)
        assertIs<ListDiff>(shorterToLongerDiff)
        assertEquals(4, shorterToLongerDiff.size)
        assertEquals(null, shorterToLongerDiff[0]) // no diff -> null
        assertEquals(UpdateOp(1, "one"), shorterToLongerDiff[1])
        assertEquals(null, shorterToLongerDiff[2]) // no diff -> null
        assertEquals(InsertOp("three"), shorterToLongerDiff[3])

        // When: diffing longer with shorter (reverse-diff of the previous operation)
        val longerToShorterDiff = calculateDifference(longer, shorter)

        // Then
        assertNotNull(longerToShorterDiff)
        assertIs<ListDiff>(longerToShorterDiff)
        assertEquals(4, longerToShorterDiff.size)
        assertEquals(null, longerToShorterDiff[0]) // no diff -> null
        assertEquals(UpdateOp("one", 1), longerToShorterDiff[1])
        assertEquals(null, longerToShorterDiff[2]) // no diff -> null
        assertEquals(RemoveOp("three"), longerToShorterDiff[3])
    }
}