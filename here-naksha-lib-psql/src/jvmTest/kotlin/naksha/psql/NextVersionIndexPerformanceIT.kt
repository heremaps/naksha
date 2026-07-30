package naksha.psql

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import naksha.base.AnyObject
import naksha.base.Int64
import naksha.geo.LineStringCoord
import naksha.geo.PointCoord
import naksha.geo.SpLineString
import naksha.model.TagList
import naksha.model.objects.Index
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardIndices
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzIndices
import naksha.model.request.ErrorResponse
import naksha.model.request.ReadFeatures
import naksha.model.request.Response
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.ops.And
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Or
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Opt-in integration benchmark comparing ActivityLog predecessor lookup index strategies.
 *
 * This test is deliberately excluded from ordinary runs. It mutates the configured test database and
 * needs enough data for PostgreSQL's planner and buffer statistics to be meaningful.
 */
class NextVersionIndexPerformanceIT {

    @Test
    fun nextVersionIndexImprovesHistoryPredecessorLookup() {
        assumeTrue(
            System.getenv(RUN_FLAG) == "true",
            "Set $RUN_FLAG=true to run the opt-in Postgres index benchmark.",
        )

        val config = BenchmarkConfig.fromEnv()
        recreateBenchmarkCatalog()
        val noIndexCollection = createBenchmarkCollection(
            NO_NXV_COLLECTION,
            listOf(XyzIndices.XyzTags, StandardIndices.Geometry),
        )
        val separateIndexCollection = createBenchmarkCollection(
            SEPARATE_NXV_COLLECTION,
            listOf(XyzIndices.XyzTags, StandardIndices.Geometry),
        )
        val indexedCollection = createBenchmarkCollection(
            WITH_NXV_COLLECTION,
            listOf(XyzIndices.XyzTags, StandardIndices.Geometry, StandardIndices.NextVersion),
        )

        val noIndexFinalFeatures = loadData(noIndexCollection, config)
        val separateIndexFinalFeatures = loadData(separateIndexCollection, config)
        val indexedFinalFeatures = loadData(indexedCollection, config)
        createStandaloneNextVersionIndices(separateIndexCollection.id)
        val noIndexRows = analyzeAndCount(noIndexCollection.id)
        val separateIndexRows = analyzeAndCount(separateIndexCollection.id)
        val indexedRows = analyzeAndCount(indexedCollection.id)
        val expectedHistoryRows = config.featureCount.toLong() * config.updateRounds.toLong()

        assertEquals(config.featureCount.toLong(), noIndexRows.headRows)
        assertEquals(expectedHistoryRows, noIndexRows.historyRows)
        assertEquals(config.featureCount.toLong(), separateIndexRows.headRows)
        assertEquals(expectedHistoryRows, separateIndexRows.historyRows)
        assertEquals(config.featureCount.toLong(), indexedRows.headRows)
        assertEquals(expectedHistoryRows, indexedRows.historyRows)
        assertCatalogShape(noIndexCollection.id, expectsNextVersion = false)
        assertCatalogShape(separateIndexCollection.id, expectsNextVersion = false)
        assertStandaloneNextVersionIndexPlacement()
        assertCatalogShape(indexedCollection.id, expectsNextVersion = true)
        assertNextVersionIndexPlacement()
        assertCoreIndexSmokePlans(noIndexCollection.id)
        assertCoreIndexSmokePlans(separateIndexCollection.id)
        assertCoreIndexSmokePlans(indexedCollection.id)

        val noIndexKeys = sampleLookupKeys(noIndexFinalFeatures, config.sampleSize)
        val separateIndexKeys = sampleLookupKeys(separateIndexFinalFeatures, config.sampleSize)
        val indexedKeys = sampleLookupKeys(indexedFinalFeatures, config.sampleSize)
        assertEquals(noIndexKeys.size, readBySuccessor(noIndexCollection.id, noIndexKeys))
        assertEquals(separateIndexKeys.size, readBySuccessor(separateIndexCollection.id, separateIndexKeys))
        assertEquals(indexedKeys.size, readBySuccessor(indexedCollection.id, indexedKeys))

        val noIndexQuery = historyLookup(noIndexCollection.id, noIndexKeys)
        val separateIndexQuery = historyLookup(separateIndexCollection.id, separateIndexKeys)
        val indexedQuery = historyLookup(indexedCollection.id, indexedKeys)
        val timings = measureAlternating(
            listOf(noIndexQuery, separateIndexQuery, indexedQuery),
            config.repeats,
        )
        val noIndexExplain = explainAnalyze(noIndexQuery)
        val separateIndexExplain = explainAnalyze(separateIndexQuery)
        val indexedExplain = explainAnalyze(indexedQuery)
        val noIndexTextPlan = explainAnalyzeText(noIndexQuery)
        val separateIndexTextPlan = explainAnalyzeText(separateIndexQuery)
        val indexedTextPlan = explainAnalyzeText(indexedQuery)
        val noIndexResult = BenchmarkResult(
            noIndexCollection.id,
            timings[0].rowCount,
            timings[0].durationsMs,
            noIndexExplain,
            noIndexTextPlan,
        )
        val separateIndexResult = BenchmarkResult(
            separateIndexCollection.id,
            timings[1].rowCount,
            timings[1].durationsMs,
            separateIndexExplain,
            separateIndexTextPlan,
        )
        val indexedResult = BenchmarkResult(
            indexedCollection.id,
            timings[2].rowCount,
            timings[2].durationsMs,
            indexedExplain,
            indexedTextPlan,
        )

        writeReport(config, listOf(
            ReportScenario("No additional next-version index", noIndexRows, noIndexResult),
            ReportScenario("Separate nv and mandatory fn indices", separateIndexRows, separateIndexResult),
            ReportScenario("Composite (nv, fn) index", indexedRows, indexedResult),
        ))

        assertEquals(noIndexKeys.size.toLong(), noIndexResult.rowCount)
        assertEquals(separateIndexKeys.size.toLong(), separateIndexResult.rowCount)
        assertEquals(indexedKeys.size.toLong(), indexedResult.rowCount)
        assertFalse(noIndexExplain.usesNextVersionIndex, noIndexExplain.plan)
        assertFalse(noIndexExplain.usesStandaloneNextVersionIndex, noIndexExplain.plan)
        assertFalse(separateIndexExplain.usesNextVersionIndex, separateIndexExplain.plan)
        assertTrue(indexedExplain.usesNextVersionIndex, indexedExplain.plan)
        assertFalse(indexedExplain.usesStandaloneNextVersionIndex, indexedExplain.plan)
        assertTrue(indexedExplain.usesIndexScan, indexedExplain.plan)
        assertTrue(
            indexedExplain.sharedBuffers < noIndexExplain.sharedBuffers,
            "Expected next_version to use fewer shared buffers: " +
                "without=${noIndexExplain.sharedBuffers}, with=${indexedExplain.sharedBuffers}",
        )
    }

    private fun recreateBenchmarkCatalog() {
        executeWrite(WriteRequest().add(Write().deleteMapById(MAP_ID)))
        executeWrite(WriteRequest().add(Write().createMap(NakshaCatalog(MAP_ID))))
    }

    private fun createBenchmarkCollection(collectionId: String, indices: List<Index>): NakshaCollection {
        val collection = NakshaCollection(collectionId, MAP_ID).withIndices(*indices.toTypedArray())
        executeWrite(WriteRequest().add(Write().createCollection(collection)))
        return collection
    }

    private fun loadData(collection: NakshaCollection, config: BenchmarkConfig): List<NakshaFeature> {
        generateFeatures(config.featureCount, round = 0)
            .chunked(config.batchSize).forEach { batch ->
                val request = WriteRequest()
                batch.forEach { request.add(Write().createFeature(collection, it)) }
                executeWrite(request)
            }

        var current = emptyList<NakshaFeature>()
        repeat(config.updateRounds) { round ->
            current = generateFeatures(config.featureCount, round + 1)
                .chunked(config.batchSize).flatMap { batch ->
                val request = WriteRequest()
                batch.forEach { desired ->
                    request.add(Write().upsertFeature(collection, desired))
                }
                executeWrite(request).nonNullFeatures()
            }
        }
        return current
    }

    private fun generateFeatures(count: Int, round: Int): List<NakshaFeature> = List(count) { index ->
        NakshaFeature(featureId(index)).apply {
            title = "idx_perf_feature_${index}_round_$round"
            geometry = lineString(index, round)
            properties.xyz.tags = tagsFor(index, round)
        }
    }

    private fun tagsFor(index: Int, round: Int): TagList =
        TagList("idx_perf", "idx_bucket_${index % 32}", "idx_round_$round")

    private fun lineString(index: Int, round: Int): SpLineString {
        val lon = -122.0 + (index % 100) * 0.0001 + round * 0.000001
        val lat = 37.0 + (index / 100) * 0.0001 + round * 0.000001
        return SpLineString(
            LineStringCoord(
                PointCoord(lon, lat),
                PointCoord(lon + 0.00005, lat + 0.00005),
                PointCoord(lon + 0.00009, lat + 0.00002),
            ),
        )
    }

    private fun featureId(index: Int): String = "idx_perf_feature_$index"

    private fun sampleLookupKeys(features: List<NakshaFeature>, requestedSampleSize: Int): List<LookupKey> {
        val sampleSize = min(requestedSampleSize, features.size)
        assertTrue(sampleSize > 0, "Sample size must be positive")
        val step = features.size.toDouble() / sampleSize.toDouble()
        return List(sampleSize) { sampleIndex ->
            val tupleNumber = assertNotNull(
                features[(sampleIndex * step).toInt()].properties.xyz.guid,
            ).tupleNumber
            LookupKey(tupleNumber.featureNumber, tupleNumber.version)
        }
    }

    private fun readBySuccessor(collectionId: String, keys: List<LookupKey>): Int {
        val predicates = keys.map { key ->
            And(
                Equals(StandardMembers.NextVersion, key.successorVersion),
                Equals(StandardMembers.FeatureNumber, key.featureNumber),
            )
        }
        val response = executeRead(
            ReadFeatures().apply {
                catalogId = MAP_ID
                this.collectionId = collectionId
                queryMembers = Or(*predicates.toTypedArray())
                queryHistory = true
            },
            PgLogLevel.EXPLAIN_AND_QUERIES,
        )
        return response.features.size
    }

    private fun historyLookup(collectionId: String, keys: List<LookupKey>): SqlQuery {
        val conditions = keys.mapIndexed { index, _ ->
            val firstParameter = index * 2 + 1
            "(nv = \$$firstParameter AND fn = \$${firstParameter + 1})"
        }
        val args = arrayOfNulls<Any?>(keys.size * 2)
        keys.forEachIndexed { index, key ->
            args[index * 2] = key.successorVersion
            args[index * 2 + 1] = key.featureNumber
        }
        return SqlQuery(
            sql = """
                SELECT /* $QUERY_MARKER */ fn, version
                FROM ${tableRef("$collectionId\$hst")}
                WHERE ${conditions.joinToString(" OR ")}
            """.trimIndent(),
            args = args,
            argTypes = Array(args.size) { PgType.INT64.text },
        )
    }

    private fun measureAlternating(queries: List<SqlQuery>, repeats: Int): List<ScenarioTiming> =
        storage.adminConnection().use { conn ->
            assertTrue(queries.isNotEmpty(), "At least one benchmark query is required")
            val plans = queries.map { conn.prepare(it.sql, it.argTypes) }
            try {
                plans.forEachIndexed { index, plan -> executeAndCount(plan, queries[index].args) }
                val durations = List(queries.size) { mutableListOf<Double>() }
                val rowCounts = LongArray(queries.size) { -1L }

                repeat(repeats) { repeatIndex ->
                    repeat(queries.size) { offset ->
                        val scenarioIndex = (repeatIndex + offset) % queries.size
                        val result = timedExecution(plans[scenarioIndex], queries[scenarioIndex].args)
                        rowCounts[scenarioIndex] = result.rowCount
                        durations[scenarioIndex] += result.durationMs
                    }
                }
                List(queries.size) { index -> ScenarioTiming(durations[index], rowCounts[index]) }
            } finally {
                plans.asReversed().forEach { it.close() }
            }
        }

    private fun timedExecution(plan: PgPlan, args: Array<Any?>): TimedExecution {
        val start = System.nanoTime()
        val rowCount = executeAndCount(plan, args)
        return TimedExecution(rowCount, (System.nanoTime() - start).toDouble() / 1_000_000.0)
    }

    private fun executeAndCount(plan: PgPlan, args: Array<Any?>): Long = plan.execute(args).use { cursor ->
        var rows = 0L
        while (cursor.next()) rows++
        rows
    }

    private fun explainAnalyze(query: SqlQuery): ExplainSummary = storage.adminConnection().use { conn ->
        conn.prepare(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ${query.sql}",
            query.argTypes,
        ).use { plan ->
            plan.execute(query.args).use { cursor ->
                assertTrue(cursor.next(), "EXPLAIN returned no rows")
                ExplainSummary.fromJson(cursor["QUERY PLAN"])
            }
        }
    }

    private fun explainAnalyzeText(query: SqlQuery): String = storage.adminConnection().use { conn ->
        conn.prepare(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) ${query.sql}",
            query.argTypes,
        ).use { plan ->
            plan.execute(query.args).use { cursor ->
                val lines = buildList<String> {
                    while (cursor.next()) add(cursor.get<String>("QUERY PLAN"))
                }
                assertTrue(lines.isNotEmpty(), "Text EXPLAIN returned no rows")
                lines.joinToString("\n")
            }
        }
    }

    private fun analyzeAndCount(collectionId: String): RowCounts {
        val historyTables = historyTables(collectionId)
        storage.adminConnection().use { conn ->
            conn.execute("ANALYZE ${tableRef(collectionId)}").close()
            historyTables.forEach { conn.execute("ANALYZE ${tableRef(it)}").close() }
            conn.commit()
        }
        return RowCounts(
            tableRowCount(collectionId),
            tableRowCount("$collectionId\$hst"),
        )
    }

    private fun tableRowCount(tableName: String): Long = storage.adminConnection().use { conn ->
        conn.execute("SELECT count(*) AS row_count FROM ${tableRef(tableName)}").use { cursor ->
            assertTrue(cursor.next(), "Count query returned no rows for $tableName")
            numberAsLong(cursor["row_count"])
        }
    }

    private fun historyTables(collectionId: String): List<String> = storage.adminConnection().use { conn ->
        conn.execute(
            """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = $1 AND tablename LIKE $2
                ORDER BY tablename
            """.trimIndent(),
            arrayOf(MAP_ID, "$collectionId\$hst\$%"),
        ).use { cursor ->
            buildList {
                while (cursor.next()) add(cursor["tablename"])
            }
        }
    }

    private fun createStandaloneNextVersionIndices(collectionId: String) {
        val historyTables = historyTables(collectionId)
        assertTrue(historyTables.isNotEmpty(), "No history tables found for $collectionId")
        storage.adminConnection().use { conn ->
            historyTables.forEach { tableName ->
                val indexName = "$tableName$STANDALONE_NEXT_VERSION_INDEX_MARKER"
                conn.execute(
                    "CREATE INDEX ${PgUtil.quoteIdent(indexName)} " +
                        "ON ${tableRef(tableName)} USING btree (nv) WITH (fillfactor=100)",
                ).close()
            }
            conn.commit()
        }
    }

    private fun assertCatalogShape(collectionId: String, expectsNextVersion: Boolean) {
        val indexes = catalogIndexes(collectionId)
        val headNames = indexes.filter { it.tableName == collectionId }.map { it.indexName }.toSet()
        assertContains(headNames, "$collectionId\$ci_tags")
        assertContains(headNames, "$collectionId\$ci_geo")
        assertFalse(headNames.contains("$collectionId\$ci_next_version"))
        assertUnrequestedXyzIndicesAbsent(collectionId, headNames)

        val historyIndexes = indexes.filter { it.tableName.startsWith("$collectionId\$hst\$") }
        assertTrue(historyIndexes.isNotEmpty(), "No history leaf indexes found for $collectionId")
        historyIndexes.groupBy { it.tableName }.forEach { (tableName, tableIndexes) ->
            val names = tableIndexes.map { it.indexName }.toSet()
            assertContains(names, "$tableName\$ci_tags")
            assertContains(names, "$tableName\$ci_geo")
            assertUnrequestedXyzIndicesAbsent(tableName, names)
        }

        val nextVersionIndexes = indexes.filter { it.indexName.contains(NEXT_VERSION_INDEX_MARKER) }
        if (expectsNextVersion) {
            assertTrue(nextVersionIndexes.isNotEmpty(), "No next_version indexes found for $collectionId")
            assertTrue(nextVersionIndexes.all { it.tableName.startsWith("$collectionId\$hst\$") })
            nextVersionIndexes.forEach { index ->
                assertTrue(
                    index.indexDef.contains("(nv, fn) INCLUDE (version)"),
                    "Unexpected next_version DDL: ${index.indexDef}",
                )
            }
        } else {
            assertTrue(nextVersionIndexes.isEmpty(), "Unexpected next_version indexes: $nextVersionIndexes")
        }
    }

    private fun assertUnrequestedXyzIndicesAbsent(tableName: String, names: Set<String>) {
        val allowed = setOf(XyzIndices.XyzTags.name, StandardIndices.Geometry.name)
        XyzIndices.ALL.filter { it.name !in allowed }.forEach { index ->
            assertFalse(
                names.contains("$tableName\$ci_${index.name}"),
                "Unexpected XYZ index ${index.name} on $tableName",
            )
        }
    }

    private fun assertNextVersionIndexPlacement() {
        val indexes = catalogIndexes(NO_NXV_COLLECTION) +
            catalogIndexes(SEPARATE_NXV_COLLECTION) +
            catalogIndexes(WITH_NXV_COLLECTION)
        val nextVersionIndexes = indexes.filter { it.indexName.contains(NEXT_VERSION_INDEX_MARKER) }
        assertTrue(nextVersionIndexes.all {
            it.tableName.startsWith("$WITH_NXV_COLLECTION\$hst\$")
        }, "next_version must exist only on indexed history leaves: $nextVersionIndexes")
    }

    private fun assertStandaloneNextVersionIndexPlacement() {
        val indexes = catalogIndexes(NO_NXV_COLLECTION) +
            catalogIndexes(SEPARATE_NXV_COLLECTION) +
            catalogIndexes(WITH_NXV_COLLECTION)
        val standaloneIndexes = indexes.filter {
            it.indexName.contains(STANDALONE_NEXT_VERSION_INDEX_MARKER)
        }
        val historyTables = historyTables(SEPARATE_NXV_COLLECTION)
        assertEquals(historyTables.size, standaloneIndexes.size)
        assertTrue(standaloneIndexes.all {
            it.tableName.startsWith("$SEPARATE_NXV_COLLECTION\$hst\$") &&
                it.indexDef.contains("USING btree (nv)") &&
                it.indexDef.contains("fillfactor='100'")
        }, "Standalone nv indices must exist only on the separate-index history leaves: $standaloneIndexes")
    }

    private fun assertCoreIndexSmokePlans(collectionId: String) {
        val idPlan = explainWithSequentialScanDisabled(
            "SELECT id, fn FROM ${tableRef(collectionId)} WHERE id = \$1",
            arrayOf(featureId(0)),
            arrayOf(PgType.STRING.text),
        )
        assertTrue(
            idPlan.contains("$collectionId\$c_id") ||
                idPlan.contains("$collectionId\$ci_id_unique") ||
                idPlan.contains("$collectionId\$ci_id"),
            "ID smoke query did not use a mandatory ID index:\n$idPlan",
        )

        val tagsPlan = explainWithSequentialScanDisabled(
            "SELECT id, fn FROM ${tableRef(collectionId)} WHERE tags @> ARRAY[\$1]::text[]",
            arrayOf("idx_bucket_0"),
            arrayOf(PgType.STRING.text),
        )
        assertTrue(tagsPlan.contains("$collectionId\$ci_tags"), tagsPlan)

        val geoPlan = explainWithSequentialScanDisabled(
            """
                SELECT id, fn FROM ${tableRef(collectionId)}
                WHERE ST_Intersects(
                    naksha_2d(geo),
                    ST_MakeEnvelope(-122.0001, 36.9999, -121.9998, 37.0002, 4326)
                )
            """.trimIndent(),
            emptyArray(),
            emptyArray(),
        )
        assertTrue(geoPlan.contains("$collectionId\$ci_geo"), geoPlan)
    }

    private fun explainWithSequentialScanDisabled(
        sql: String,
        args: Array<Any?>,
        types: Array<String>,
    ): String = storage.adminConnection().use { conn ->
        conn.execute("SET enable_seqscan = off").close()
        conn.prepare("EXPLAIN $sql", types).use { plan ->
            plan.execute(args).use { cursor ->
                buildList {
                    while (cursor.next()) add(cursor["QUERY PLAN"] as String)
                }.joinToString("\n")
            }
        }
    }

    private fun catalogIndexes(collectionId: String): List<CatalogIndex> =
        storage.adminConnection().use { conn ->
            conn.execute(
                """
                    SELECT schemaname, tablename, indexname, indexdef
                    FROM pg_indexes
                    WHERE schemaname = $1 AND tablename LIKE $2
                    ORDER BY schemaname, tablename, indexname
                """.trimIndent(),
                arrayOf(MAP_ID, "$collectionId%"),
            ).use { cursor ->
                buildList {
                    while (cursor.next()) {
                        add(CatalogIndex(
                            cursor["schemaname"],
                            cursor["tablename"],
                            cursor["indexname"],
                            cursor["indexdef"],
                        ))
                    }
                }
            }
        }

    private fun executeWrite(request: WriteRequest): SuccessResponse =
        storage.newWriteSession(PgTestBase.newSessionOptions()).use { session ->
            val response = assertSuccess(session.execute(request))
            session.commit()
            response
        }

    private fun executeRead(request: ReadFeatures, logLevel: String): SuccessResponse =
        storage.newReadSession(PgTestBase.newSessionOptions(logLevel = logLevel)).use { session ->
            assertSuccess(session.execute(request))
        }

    private fun assertSuccess(response: Response): SuccessResponse {
        if (response is ErrorResponse) response.error.print()
        val detail = if (response is ErrorResponse) {
            ": ${response.error.code} ${response.error.msg}"
        } else {
            ""
        }
        assertTrue(
            response is SuccessResponse,
            "Expected SuccessResponse, got ${response::class.simpleName}$detail",
        )
        return response
    }

    private fun SuccessResponse.nonNullFeatures(): List<NakshaFeature> =
        features.map { assertNotNull(it, "Write response contained a null feature") }

    private fun writeReport(config: BenchmarkConfig, scenarios: List<ReportScenario>) {
        val catalog = scenarios.flatMap { catalogIndexes(it.result.collectionId) }
        val report = buildString {
            appendLine("# Next Version Index Benchmark")
            appendLine()
            appendLine("## Configuration")
            appendLine()
            appendLine("| Setting | Value |")
            appendLine("| --- | ---: |")
            appendLine("| Features | ${config.featureCount} |")
            appendLine("| Update rounds | ${config.updateRounds} |")
            appendLine("| Sample size | ${config.sampleSize} |")
            appendLine("| Repeats | ${config.repeats} |")
            appendLine("| Batch size | ${config.batchSize} |")
            appendLine()
            appendLine("## Row Counts")
            appendLine()
            appendLine("| Collection | HEAD | HISTORY |")
            appendLine("| --- | ---: | ---: |")
            scenarios.forEach {
                appendLine("| ${it.result.collectionId} | ${it.rowCounts.headRows} | ${it.rowCounts.historyRows} |")
            }
            appendLine()
            appendLine("## Results")
            appendLine()
            appendLine(
                "| Collection | Rows | Avg ms | Min ms | Max ms | JSON shared buffers | " +
                    "Uses `$STANDALONE_NEXT_VERSION_INDEX_MARKER` | Uses `$NEXT_VERSION_INDEX_MARKER` |",
            )
            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |")
            scenarios.forEach { appendLine(it.result.reportRow()) }
            appendLine()
            appendLine("## Catalog Snapshot")
            appendLine()
            appendLine("```text")
            catalog.forEach { appendLine("${it.schemaName}.${it.tableName} ${it.indexName} ${it.indexDef}") }
            appendLine("```")
            appendLine()
            appendLine("## pg_stat_user_indexes Snapshot")
            appendLine()
            appendLine("```text")
            appendLine(indexStatsSnapshot())
            appendLine("```")
            appendLine()
            appendLine("## pg_stat_statements Snapshot")
            appendLine()
            appendLine("```text")
            appendLine(pgStatStatementsSnapshot())
            appendLine("```")
            appendLine()
            appendLine("## EXPLAIN Plan Notes")
            appendLine()
            appendLine("Each text plan and JSON plan is produced by a separate `EXPLAIN ANALYZE` execution.")
            appendLine("The results table and automated assertions use the JSON execution; text plans are included for human inspection.")
            appendLine()
            scenarios.forEach { scenario ->
                appendLine("## EXPLAIN: ${scenario.title}")
                appendLine()
                appendLine("### Text Plan")
                appendLine()
                appendLine("```text")
                appendLine(scenario.result.textPlan)
                appendLine("```")
                appendLine()
                appendLine("### JSON Plan")
                appendLine()
                appendLine("```json")
                appendLine(scenario.result.explain.plan)
                appendLine("```")
                appendLine()
            }
        }
        Files.createDirectories(REPORT_PATH.parent)
        Files.writeString(REPORT_PATH, report)
    }

    private fun BenchmarkResult.reportRow(): String =
        "| $collectionId | $rowCount | ${durationsMs.average().formatMs()} | " +
            "${durationsMs.minOrNull().formatMs()} | ${durationsMs.maxOrNull().formatMs()} | " +
            "${explain.sharedBuffers} | ${explain.usesStandaloneNextVersionIndex} | " +
            "${explain.usesNextVersionIndex} |"

    private fun Double?.formatMs(): String = if (this == null) "n/a" else "%.3f".format(this)

    private fun indexStatsSnapshot(): String = querySnapshot(
        """
            SELECT relname, indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
            FROM pg_stat_user_indexes
            WHERE schemaname = $1 AND (indexrelname LIKE $2 OR indexrelname LIKE $3)
            ORDER BY relname, indexrelname
        """.trimIndent(),
        arrayOf(
            MAP_ID,
            "%$STANDALONE_NEXT_VERSION_INDEX_MARKER%",
            "%$NEXT_VERSION_INDEX_MARKER%",
        ),
    )

    private fun pgStatStatementsSnapshot(): String = querySnapshot(
        """
            SELECT calls, mean_exec_time, rows, shared_blks_hit, shared_blks_read, query
            FROM pg_stat_statements
            WHERE query ILIKE '%$QUERY_MARKER%'
            ORDER BY mean_exec_time DESC
            LIMIT 20
        """.trimIndent(),
        emptyArray(),
    )

    private fun querySnapshot(sql: String, args: Array<Any?>): String = try {
        storage.adminConnection().use { conn ->
            conn.execute(sql, args).use { cursor ->
                buildList {
                    while (cursor.next()) {
                        add(cursor.map(AnyObject::class).entries.joinToString(
                            prefix = "{",
                            postfix = "}",
                        ) { (key, value) -> "$key=$value" })
                    }
                }.ifEmpty { listOf("(no rows)") }.joinToString("\n")
            }
        }
    } catch (e: Exception) {
        "Unavailable: ${e.message}"
    }

    private fun tableRef(tableName: String): String =
        "${PgUtil.quoteIdent(MAP_ID)}.${PgUtil.quoteIdent(tableName)}"

    private fun numberAsLong(value: Any): Long = when (value) {
        is Int -> value.toLong()
        is Long -> value
        is Int64 -> value.toLong()
        is Number -> value.toLong()
        else -> value.toString().toLong()
    }

    private val storage: PgStorage
        get() = PgTestBase.storage

    private data class BenchmarkConfig(
        val featureCount: Int,
        val updateRounds: Int,
        val sampleSize: Int,
        val repeats: Int,
        val batchSize: Int,
    ) {
        companion object {
            fun fromEnv(): BenchmarkConfig = BenchmarkConfig(
                positiveEnvInt("NAKSHA_INDEX_PERF_FEATURES", 6_000),
                positiveEnvInt("NAKSHA_INDEX_PERF_UPDATES", 9),
                positiveEnvInt("NAKSHA_INDEX_PERF_SAMPLE_SIZE", 100),
                positiveEnvInt("NAKSHA_INDEX_PERF_REPEATS", 20),
                positiveEnvInt("NAKSHA_INDEX_PERF_BATCH_SIZE", 500),
            )

            private fun positiveEnvInt(name: String, default: Int): Int =
                System.getenv(name)?.toIntOrNull()?.takeIf { it > 0 } ?: default
        }
    }

    private data class LookupKey(val featureNumber: Int64, val successorVersion: Int64)
    private data class SqlQuery(val sql: String, val args: Array<Any?>, val argTypes: Array<String>)
    private data class RowCounts(val headRows: Long, val historyRows: Long)
    private data class TimedExecution(val rowCount: Long, val durationMs: Double)
    private data class ScenarioTiming(val durationsMs: List<Double>, val rowCount: Long)
    private data class BenchmarkResult(
        val collectionId: String,
        val rowCount: Long,
        val durationsMs: List<Double>,
        val explain: ExplainSummary,
        val textPlan: String,
    )
    private data class CatalogIndex(
        val schemaName: String,
        val tableName: String,
        val indexName: String,
        val indexDef: String,
    )
    private data class ReportScenario(
        val title: String,
        val rowCounts: RowCounts,
        val result: BenchmarkResult,
    )

    private data class ExplainSummary(
        val plan: String,
        val sharedBuffers: Long,
        val executionTimeMs: Double?,
        val usesStandaloneNextVersionIndex: Boolean,
        val usesNextVersionIndex: Boolean,
        val usesIndexScan: Boolean,
    ) {
        companion object {
            private val mapper = ObjectMapper()

            fun fromJson(planJson: String): ExplainSummary {
                val root = mapper.readTree(planJson)
                val planNode = root[0]["Plan"]
                val indexNames = mutableListOf<String>()
                val nodeTypes = mutableListOf<String>()
                collectPlanDetails(planNode, indexNames, nodeTypes)
                return ExplainSummary(
                    plan = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    sharedBuffers = sharedBuffers(planNode),
                    executionTimeMs = root[0]["Execution Time"]?.asDouble(),
                    usesStandaloneNextVersionIndex = indexNames.any {
                        it.contains(STANDALONE_NEXT_VERSION_INDEX_MARKER)
                    },
                    usesNextVersionIndex = indexNames.any { it.contains(NEXT_VERSION_INDEX_MARKER) },
                    usesIndexScan = nodeTypes.any {
                        it == "Index Scan" || it == "Index Only Scan" || it == "Bitmap Index Scan"
                    },
                )
            }

            private fun collectPlanDetails(
                node: JsonNode,
                indexNames: MutableList<String>,
                nodeTypes: MutableList<String>,
            ) {
                node["Index Name"]?.asText()?.let(indexNames::add)
                node["Node Type"]?.asText()?.let(nodeTypes::add)
                node["Plans"]?.forEach { collectPlanDetails(it, indexNames, nodeTypes) }
            }

            private fun sharedBuffers(node: JsonNode): Long {
                val direct = node.path("Shared Hit Blocks").asLong() + node.path("Shared Read Blocks").asLong()
                if (direct > 0) return direct
                return node["Plans"]?.sumOf(::sharedBuffers) ?: 0L
            }
        }
    }

    companion object {
        private const val RUN_FLAG = "NAKSHA_RUN_INDEX_PERF_TEST"
        private const val MAP_ID = "idx_perf_map"
        private const val NO_NXV_COLLECTION = "idx_perf_no_nxv"
        private const val SEPARATE_NXV_COLLECTION = "idx_perf_separate_nxv"
        private const val WITH_NXV_COLLECTION = "idx_perf_with_nxv"
        private const val STANDALONE_NEXT_VERSION_INDEX_MARKER = "\$ci_nv_only"
        private const val NEXT_VERSION_INDEX_MARKER = "\$ci_next_version"
        private const val QUERY_MARKER = "naksha-index-perf-next-version"
        private val REPORT_PATH: Path = Paths.get("build/reports/naksha-index-perf/next-version-index.md")
    }
}
