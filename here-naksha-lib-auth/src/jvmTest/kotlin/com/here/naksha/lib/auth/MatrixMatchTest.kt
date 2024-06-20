package com.here.naksha.lib.auth

import arrow.core.success
import com.fasterxml.jackson.databind.ObjectMapper
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import io.kotlintest.inspectors.runTests
import naksha.base.JvmMap
import naksha.base.Platform
import naksha.base.Proxy
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
            fun from(jsonFile: File): TestCase {
                val root = Platform.fromJSON(jsonFile.readText()) as JvmMap
                val urm = Proxy.box(root["urm"]!!, UserRightsMatrix::class)!!
                val arm = Proxy.box(root["arm"]!!, AccessRightsMatrix::class)!!
                val shouldMatch = Proxy.box(root["matches"], Boolean::class)!!
                return TestCase(jsonFile.name, urm, arm, shouldMatch)
            }
        }
    }
}