package naksha.diff

import naksha.base.Platform
import naksha.diff.DifferenceCalculator.DifferenceCalculator_C.calculateDifference
import kotlin.test.*

class DifferenceCalculatorTest {

    @Test
    fun x(){
        val objectToPatch = Platform.fromJson("""
            {
                "foo": "bar",
                "lorem": "ipsum"
            }
        """.trimIndent())!!

        val diff = MapDiff()
        diff["foo"] = UpdateDiff(oldValue = "bar", newValue = "new_bar")
        diff["lorem"] = RemoveDiff(oldValue = "ipsum")
        diff["new_field"] = InsertDiff(newValue = 123)

        // Ensure that the diff is correct
        assertEquals(3, diff.size)
        val entryIt = diff.entries.iterator()
        assertTrue(entryIt.hasNext())
        val fooEntry = entryIt.next()
        assertEquals("foo", fooEntry.key)
        assertIs<UpdateDiff>(fooEntry.value)

        Patcher.patch(objectToPatch, diff)
        val x = Platform.toJson(objectToPatch)
    }

    @Test
    fun shouldTreatMissingSourceAsInsertion() {
        // Given
        val target = "some_value"

        // When
        val diff = calculateDifference(source = null, target = target)

        // Then
        assertIs<InsertDiff>(diff)
        assertEquals(target, diff.newValue)
    }

    @Test
    fun shouldTreatMissingTargetAsRemoval() {
        // Given
        val source = 123

        // When
        val diff = calculateDifference(source = source, target = null)

        // Then
        assertIs<RemoveDiff>(diff)
        assertEquals(source, diff.oldValue)
    }

    @Test
    fun shouldCalculateDiffForSimpleMaps() {
        // Given:
        val left = Platform.fromJson(
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
        val right = Platform.fromJson(
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
        assertTrue(leftToRightDiff.differences.keys.containsAll(setOf("age", "address")))
        assertEquals(2, leftToRightDiff.differences.size)
        assertEquals(UpdateDiff(37, 39), leftToRightDiff.differences["age"])
        val leftToRightAddressChange = leftToRightDiff.differences["address"]
        assertIs<MapDiff>(leftToRightAddressChange)
        assertEquals(UpdateDiff("London", "Fordwich"), leftToRightAddressChange.differences["city"])
        assertEquals(RemoveDiff("Abbey Road"), leftToRightAddressChange.differences["street"])
        assertEquals(UpdateDiff(12, 71), leftToRightAddressChange.differences["houseNo"])

        // When
        val rightToLeftDiff = calculateDifference(right, left)

        // Then
        assertNotNull(rightToLeftDiff)
        assertIs<MapDiff>(rightToLeftDiff)
        assertTrue(rightToLeftDiff.differences.keys.containsAll(setOf("age", "address")))
        assertEquals(2, rightToLeftDiff.differences.size)
        assertEquals(UpdateDiff(39, 37), rightToLeftDiff.differences["age"])
        val rightToLeftAddressChange = rightToLeftDiff.differences["address"]
        assertIs<MapDiff>(rightToLeftAddressChange)
        assertEquals(UpdateDiff("Fordwich", "London"), rightToLeftAddressChange.differences["city"])
        assertEquals(InsertDiff("Abbey Road"), rightToLeftAddressChange.differences["street"])
        assertEquals(UpdateDiff(71, 12), rightToLeftAddressChange.differences["houseNo"])
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
        assertEquals(4, shorterToLongerDiff.differences.size)
        assertEquals(null, shorterToLongerDiff.differences[0]) // no diff -> null
        assertEquals(UpdateDiff(1, "one"), shorterToLongerDiff.differences[1])
        assertEquals(null, shorterToLongerDiff.differences[2]) // no diff -> null
        assertEquals(InsertDiff("three"), shorterToLongerDiff.differences[3])

        // When: diffing longer with shorter (reverse-diff of the previous operation)
        val longerToShorterDiff = calculateDifference(longer, shorter)

        // Then
        assertNotNull(longerToShorterDiff)
        assertIs<ListDiff>(longerToShorterDiff)
        assertEquals(4, longerToShorterDiff.differences.size)
        assertEquals(null, longerToShorterDiff.differences[0]) // no diff -> null
        assertEquals(UpdateDiff("one", 1), longerToShorterDiff.differences[1])
        assertEquals(null, longerToShorterDiff.differences[2]) // no diff -> null
        assertEquals(RemoveDiff("three"), longerToShorterDiff.differences[3])
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
        val left = Platform.fromJson("""
            {
                "foo": "abc",
                "bar": 123
            }
        """.trimIndent())

        // And:
        val right = Platform.fromJson("""
            {
                "foo": "abc",
                "bar": 456
            }
        """.trimIndent())

        // And:
        val diffContext = object : DefaultDiffContext() {
            override fun ignore(key: Any, sourceMap: Map<*, *>, targetOrPatchMap: Map<*, *>): Boolean =
                key == "bar"
        }

        // When:
        val diffWithIgnore = calculateDifference(left, right, diffContext)

        // Then: foo remained the same, bar is ignored -> there should be no diff
        assertNull(diffWithIgnore)

        // When: running the same diff without ignoring results in some diff
        val diffWithoutIgnore = calculateDifference(left, right)

        // Then
        assertIs<MapDiff>(diffWithoutIgnore)
        assertEquals(UpdateDiff(123, 456), diffWithoutIgnore.differences["bar"])
    }

    @Test
    fun shouldCalculateDiffForComplexMaps() {
        // Given:
        val left = Platform.fromJson(
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
        val right = Platform.fromJson(
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
        val staffDiff = diff.differences["staff"]
        assertIs<ListDiff>(staffDiff)
        val firstEmployee = staffDiff.differences[0]
        assertIs<MapDiff>(firstEmployee)
        assertEquals(UpdateDiff("junior", "senior"), firstEmployee.differences["seniority"])
        val firstEmployeeRoles = firstEmployee.differences["roles"]
        assertIs<ListDiff>(firstEmployeeRoles)
        val firstRoleOfFirstEmployee = firstEmployeeRoles.differences[0]
        assertIs<MapDiff>(firstRoleOfFirstEmployee)
        assertEquals(UpdateDiff("s1", "s2"), firstRoleOfFirstEmployee.differences["system"])
        val secondRoleOfFirstEmployee = firstEmployeeRoles.differences[1]
        assertIs<RemoveDiff>(secondRoleOfFirstEmployee)
        val removedRoleOfFirstEmployee = secondRoleOfFirstEmployee.oldValue
        assertIs<Map<*, *>>(removedRoleOfFirstEmployee)
        assertEquals("s2", removedRoleOfFirstEmployee["system"])
        assertEquals("user", removedRoleOfFirstEmployee["role"])
        val secondEmployee = staffDiff.differences[1]
        assertIs<MapDiff>(secondEmployee)
        assertEquals(UpdateDiff("Phil", "Stan"), secondEmployee.differences["name"])
        assertEquals(UpdateDiff(456, 999), secondEmployee.differences["id"])
        assertEquals(RemoveDiff("senior"), secondEmployee.differences["seniority"])
        val secondEmployeeRoles = secondEmployee.differences["roles"]
        assertIs<ListDiff>(secondEmployeeRoles)
        val firstRoleOfSecondEmployeeOp = secondEmployeeRoles.differences[0]
        assertIs<RemoveDiff>(firstRoleOfSecondEmployeeOp)
        val firstRemovedRoleOfSecondEmployee = firstRoleOfSecondEmployeeOp.oldValue
        assertIs<Map<*, *>>(firstRemovedRoleOfSecondEmployee)
        assertEquals("s1", firstRemovedRoleOfSecondEmployee["system"])
        assertEquals("admin", firstRemovedRoleOfSecondEmployee["role"])
        val secondRoleOfSecondEmployeeOp = secondEmployeeRoles.differences[1]
        assertIs<RemoveDiff>(secondRoleOfSecondEmployeeOp)
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
        assertEquals(4, shorterToLongerDiff.differences.size)
        assertEquals(null, shorterToLongerDiff.differences[0]) // no diff -> null
        assertEquals(UpdateDiff(1, "one"), shorterToLongerDiff.differences[1])
        assertEquals(null, shorterToLongerDiff.differences[2]) // no diff -> null
        assertEquals(InsertDiff("three"), shorterToLongerDiff.differences[3])

        // When: diffing longer with shorter (reverse-diff of the previous operation)
        val longerToShorterDiff = calculateDifference(longer, shorter)

        // Then
        assertNotNull(longerToShorterDiff)
        assertIs<ListDiff>(longerToShorterDiff)
        assertEquals(4, longerToShorterDiff.differences.size)
        assertEquals(null, longerToShorterDiff.differences[0]) // no diff -> null
        assertEquals(UpdateDiff("one", 1), longerToShorterDiff.differences[1])
        assertEquals(null, longerToShorterDiff.differences[2]) // no diff -> null
        assertEquals(RemoveDiff("three"), longerToShorterDiff.differences[3])
    }
}