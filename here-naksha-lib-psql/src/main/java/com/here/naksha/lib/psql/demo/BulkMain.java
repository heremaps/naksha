/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.psql.demo;

import static com.here.naksha.lib.psql.PsqlStorageConfig.configFromFileOrEnv;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.SimpleTask;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.psql.PsqlFeatureGenerator;
import com.here.naksha.lib.psql.PsqlHelper;
import com.here.naksha.lib.psql.PsqlStorage;
import com.here.naksha.lib.psql.PsqlStorage.Params;
import com.here.naksha.lib.psql.PsqlStorageConfig;
import com.here.naksha.lib.psql.PsqlWriteSession;
import com.vividsolutions.jts.geom.Geometry;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.postgresql.jdbc.PgConnection;
import org.postgresql.util.PGobject;

public class BulkMain {

  // Place the JDBC URL of the database into ~/.config/naksha/test_perf_db.url
  private static final @NotNull PsqlStorageConfig perfTestDb =
      configFromFileOrEnv("test_perf_db.url", "NAKSHA_TEST_PERF_DB_URL", "java_perf_test");
  private static final String ID_PREFIX = "urn:here::here:Topology:";

  private static int PARTITIONS;
  private static int CHUNK_SIZE;
  private static boolean USE_TOPOLOGY;
  private static boolean USE_SQL;
  private static String DB_COLLECTION;
  private static String TEMPLATE;
  private static final NakshaContext context = new NakshaContext().withAppId("performance_test");

  private static @NotNull Long writeFeatures(
      final @NotNull PsqlStorage storage, int partitionId, @NotNull List<@NotNull String> ids) {
    final int LEN = ids.size();
    final PsqlFeatureGenerator fg = new PsqlFeatureGenerator();
    long total_nanos = 0, start, end;
    int chunk = 0, pos = 0;
    out.printf("\t[%3d] Start execute\n", partitionId);
    try (final PsqlWriteSession session = storage.newWriteSession(context, true)) {
      session.setFetchSize(10);
      session.setStatementTimeout(15, TimeUnit.MINUTES);
      while (pos < LEN) {
        final int featuresInChunk = Math.min(LEN - pos, CHUNK_SIZE);
        out.printf(
            "\t[%3d] Create request for chunk #%d with %d features\n", partitionId, chunk, featuresInChunk);
        final WriteXyzFeatures request = new WriteXyzFeatures(DB_COLLECTION, CHUNK_SIZE);
        request.minResults = true;
        for (int i = 0; i < featuresInChunk; i++) {
          final XyzFeatureCodec codec = request.getCodecFactory().newInstance();
          codec.setOp(EWriteOp.CREATE);
          final String id = ids.get(pos++);
          codec.setId(id);
          codec.setUuid(null);
          codec.setJson(fg.newJsonFeature(TEMPLATE, null, id));
          final Geometry geometry =
              (USE_TOPOLOGY ? fg.newRandomLineString() : fg.newRandomPoint()).getJTSGeometry();
          codec.setGeometry(geometry);
          codec.setDecoded(true);
          request.features.add(codec);
        }
        out.printf(
            "\t[%3d] Execute request for chunk #%d with %d features\n",
            partitionId, chunk, featuresInChunk);
        out.flush();
        start = System.nanoTime();
        try {
          session.execute(request).close();
          session.commit(true);
        } catch (Exception e) {
          e.printStackTrace(err);
          err.flush();
          session.rollback(true);
        } finally {
          end = System.nanoTime();
          total_nanos += end - start;
          chunk++;
        }
      }
    } catch (Exception e) {
      e.printStackTrace(err);
      err.flush();
    }
    out.printf(
        "\t[%3d] Partition is done, features written: %d in %d seconds.\n",
        partitionId, LEN, NANOSECONDS.toSeconds(total_nanos));
    out.flush();
    return total_nanos;
  }

  private static @NotNull Long writeFeaturesUsingInsert(
      final @NotNull PsqlStorage storage, int partitionId, @NotNull List<@NotNull String> ids) {
    final int LEN = ids.size();
    final PsqlFeatureGenerator fg = new PsqlFeatureGenerator();
    long total_nanos = 0, start, end;
    int chunk = 0, pos = 0;
    out.printf("\t[%3d] Start execute\n", partitionId);
    try (final PsqlWriteSession session = storage.newWriteSession(context, true)) {
      session.setFetchSize(10);
      session.setStatementTimeout(15, TimeUnit.MINUTES);
      final PgConnection conn = PsqlHelper.getPgConnection(session);
      while (pos < LEN) {
        final int featuresInChunk = Math.min(LEN - pos, CHUNK_SIZE);
        out.printf(
            "\t[%3d] Execute request for chunk #%d with %d features\n",
            partitionId, chunk, featuresInChunk);
        out.flush();
        start = System.nanoTime();
        final String SQL = "INSERT INTO " + DB_COLLECTION + " (jsondata, geo) VALUES (?, ST_Force3D(?));";
        try (final PreparedStatement stmt = conn.prepareStatement(SQL);
            final Json jp = Json.get()) {
          for (int i = 0; i < featuresInChunk; i++) {
            final String id = ids.get(pos++);
            final Geometry geometry =
                (USE_TOPOLOGY ? fg.newRandomLineString() : fg.newRandomPoint()).getJTSGeometry();
            final String jsonText = fg.newJsonFeature(TEMPLATE, null, id);
            final PGobject jsondata = new PGobject();
            jsondata.setType("jsonb");
            jsondata.setValue(jsonText);
            stmt.setObject(1, jsondata);
            stmt.setBytes(2, jp.wkbWriter.write(geometry));
            stmt.addBatch();
          }
          stmt.executeBatch();
          session.commit(true);
        } catch (Exception e) {
          e.printStackTrace(err);
          err.flush();
          session.rollback(true);
        } finally {
          end = System.nanoTime();
          total_nanos += end - start;
          chunk++;
        }
      }
    } catch (Exception e) {
      e.printStackTrace(err);
      err.flush();
    }
    out.printf(
        "\t[%3d] Partition is done, features written: %d in %d seconds.\n",
        partitionId, LEN, NANOSECONDS.toSeconds(total_nanos));
    out.flush();
    return total_nanos;
  }

  public static void main(String... args) throws Exception {
    if (args.length < 4) {
      out.println(
          "Syntax: java -Xmx16g -XX:+UseZGC -cp naksha-2.0.10-all.jar com.here.naksha.lib.psql.demo.BulkMain {features} {chunkSize} {partition} {point|topology} [sql]");
      System.exit(1);
    }
    final int LIMIT = Math.max(1000, Math.min(1024 * 1024 * 1024, Integer.parseInt(args[0], 10)));
    CHUNK_SIZE = Math.max(100, Math.min(10_000, Integer.parseInt(args[1], 10)));
    PARTITIONS = Math.max(1, Math.min(256, Integer.parseInt(args[2], 10)));
    USE_TOPOLOGY = "topology".equalsIgnoreCase(args[3]);
    USE_SQL = args.length >= 5 && "sql".equalsIgnoreCase(args[4]);
    DB_COLLECTION = USE_TOPOLOGY ? "topology_test" : "point_test";
    out.printf(
        "Generate %d random %s features, in chunks of %d using %d partitions%s\n",
        LIMIT, USE_TOPOLOGY ? "topology" : "point", CHUNK_SIZE, PARTITIONS, USE_SQL ? " using SQL" : "");
    final PsqlFeatureGenerator fg = new PsqlFeatureGenerator();
    TEMPLATE = USE_TOPOLOGY ? fg.topologyTemplate() : fg.pointTemplate();
    final byte[] TEMPLATE_BYTES = TEMPLATE.getBytes(StandardCharsets.UTF_8);
    long rawSize = TEMPLATE_BYTES.length;
    long compressedSize = 0;
    try {
      final ByteArrayOutputStream baOut = new ByteArrayOutputStream();
      try (final GZIPOutputStream gzipOut = new GZIPOutputStream(baOut)) {
        gzipOut.write(TEMPLATE_BYTES);
      }
      compressedSize = baOut.toByteArray().length;
    } catch (Exception ignore) {
    }
    out.printf(
        Locale.US,
        "Template raw size: %,d, compressed-size: %,d\nChunk raw-size: %,d, compressed-size: %,d\n",
        rawSize,
        compressedSize,
        rawSize * CHUNK_SIZE,
        compressedSize * CHUNK_SIZE);
    out.printf(
        Locale.US,
        "Total amount of bytes to write:\n\tRaw: %,d\n\tCompressed: %,d\n",
        rawSize * LIMIT,
        compressedSize * LIMIT);
    try (final PsqlStorage storage = new PsqlStorage(perfTestDb)) {
      storage.setSocketTimeout(15, TimeUnit.MINUTES);
      out.println("Drop the schema " + storage.getSchema());
      storage.dropSchema();
      out.println("Create the schema " + storage.getSchema());
      storage.initStorage(new Params().pg_hint_plan(false).pg_stat_statements(true));
      out.println("Create the collection " + DB_COLLECTION);
      try (final PsqlWriteSession session = storage.newWriteSession(context, true)) {
        final WriteXyzCollections writeCollections = new WriteXyzCollections();
        writeCollections.add(EWriteOp.CREATE, new XyzCollection(DB_COLLECTION, true, false, true));
        session.execute(writeCollections).close();
        session.commit(true);
      }

      // Done, write features.
      //noinspection unchecked
      final Future<Long>[] results = (Future<Long>[]) new Future[PARTITIONS];
      out.println("Generate the IDs");
      //noinspection unchecked
      final List<String>[] ids = (List<String>[]) new ArrayList[PARTITIONS];
      //noinspection unchecked
      HashMap<String, Boolean>[] idsMap = (HashMap<String, Boolean>[]) new HashMap[PARTITIONS];
      for (int i = 0; i < ids.length; i++) {
        ids[i] = new ArrayList<>(LIMIT / CHUNK_SIZE + 100);
        idsMap[i] = new HashMap<>(LIMIT / CHUNK_SIZE + 100);
      }
      for (int i = 0; i < LIMIT; i++) {
        final String id = ID_PREFIX + RandomStringUtils.randomAlphanumeric(20);
        final int partitionId = PsqlHelper.partitionId(id) % PARTITIONS;
        if (idsMap[partitionId].putIfAbsent(id, Boolean.TRUE) != null) {
          out.printf("\tGenerated duplicate, re-generate %s", id);
          i--;
          continue;
        }
        ids[partitionId].add(id);
        if (i % 250_000 == 0) {
          out.format("Generated %d ids ...\n", i);
        }
      }
      //noinspection UnusedAssignment
      idsMap = null; // allow GC collection
      out.println("Done generating ids");
      out.println("Start writing the features");
      final long START = System.nanoTime();
      for (int i = 0; i < PARTITIONS; i++) {
        results[i] = new SimpleTask<Long>()
            .start(
                USE_SQL ? BulkMain::writeFeaturesUsingInsert : BulkMain::writeFeatures,
                storage,
                i,
                ids[i]);
      }
      long total = 0L;
      for (int i = 0; i < PARTITIONS; i++) {
        final long nanos = results[i].get();
        out.printf("[%3d] Partition is done in %d seconds.\n", i, NANOSECONDS.toSeconds(nanos));
        total += nanos;
      }
      final long END = System.nanoTime();
      final long SECONDS = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(END - START));
      // final long SECONDS = Math.max(1, NANOSECONDS.toSeconds(total));
      out.printf(
          "Done, wrote %d features in %d seconds with a rate of %.1f%n features per second\n",
          LIMIT, SECONDS, ((double) LIMIT / (double) SECONDS));
      out.flush();
      System.exit(0);
    }
  }
}
