package com.here.naksha.lib.auth

import arrow.core.success
import com.fasterxml.jackson.databind.ObjectMapper
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import io.kotlintest.inspectors.runTests
import java.io.File
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class MatrixMatchTest {

    @Test
    fun testJsonCases() {
        for (tc in testCases()) {
            assertEquals(tc.shouldMatch, tc.urm.matches(tc.arm), "Unexpected match result for '${tc.fileName}'")
        }
    }

    private fun testCases(): List<TestCase> {
        return MatrixMatchTest::class.java
            .getResource(TEST_CASES_DIR)!!.file
            .let { Path(it).toFile() }
            .listFiles()!!
            .map { TestCase.from(it) }
            .toList()
    }

    companion object {
        const val TEST_CASES_DIR = "/matrices_tests"
    }

    data class TestCase(
        val fileName: String,
        val urm: UserRightsMatrix,
        val arm: AccessRightsMatrix,
        val shouldMatch: Boolean
    ) {

        companion object {
            private val OBJECT_MAPPER = ObjectMapper()
            fun from(jsonFile: File): TestCase {
                val tree = OBJECT_MAPPER.readTree(jsonFile)
                val urm = tree["urm"]?.let { AuthParser.parseUrm(it) }
                    ?: throw IllegalArgumentException("Missing 'urm' node")
                val arm = tree["arm"]?.let { AuthParser.parseArm(it) }
                    ?: throw IllegalArgumentException("Missing 'arm' node")
                val shouldMatch = tree["matches"]?.asBoolean()
                    ?: throw IllegalArgumentException("Missing 'matches' boolean value")
                return TestCase(jsonFile.name, urm, arm, shouldMatch)
            }
        }
    }
}