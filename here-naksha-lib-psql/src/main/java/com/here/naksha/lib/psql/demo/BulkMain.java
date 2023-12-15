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

import static java.lang.System.err;
import static java.lang.System.out;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.SimpleTask;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.XyzCollection;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.WriteXyzCollections;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import com.here.naksha.lib.psql.PsqlFeatureGenerator;
import com.here.naksha.lib.psql.PsqlHelper;
import com.here.naksha.lib.psql.PsqlStorage;
import com.here.naksha.lib.psql.PsqlStorage.Params;
import com.here.naksha.lib.psql.PsqlWriteSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;

public class BulkMain {

  private static final String DB_URL =
      "jdbc:postgresql://naksha-perftest.ccmrakfzvsi3.us-east-1.rds.amazonaws.com:5432/unimap?schema=java_perf_test&id=java_perf_test&app=Naksha-Perf-Test&user=postgres&password=csdhf78wef34f";

  private static int PARTITIONS = 256;
  private static int CHUNK_SIZE = 500;
  private static final String DB_COLLECTION = "topology_test";
  private static final NakshaContext context = new NakshaContext().withAppId("performance_test");

  private static @NotNull Long writeFeatures(
      final @NotNull PsqlStorage storage, int partitionId, @NotNull List<@NotNull String> ids) {
    final int LEN = ids.size();
    final PsqlFeatureGenerator fg = new PsqlFeatureGenerator();
    long total_nanos = 0, start, end;
    int chunk = 0, pos = 0;
    out.printf("\t[%3d] Create random features\n", partitionId);
    final XyzFeature[] features = new XyzFeature[CHUNK_SIZE];
    final long START = System.nanoTime();
    for (int i = 0; i < features.length; i++) {
      features[i] = fg.newRandomFeature();
    }
    final long END = System.nanoTime();
    out.printf(
        "\t[%3d] Create %d random features in %.2f millis\n",
        partitionId, features.length, (END - START) / 1_000_000d);

    try (final PsqlWriteSession session = storage.newWriteSession(context, true)) {
      session.setFetchSize(10);
      session.setStatementTimeout(1, TimeUnit.MINUTES);
      while (pos < LEN) {
        final int featuresInChunk = Math.min(LEN - pos, CHUNK_SIZE);
        out.printf(
            "\t[%3d] Create request for chunk #%d with %d features\n", partitionId, chunk, featuresInChunk);
        final WriteXyzFeatures request = new WriteXyzFeatures(DB_COLLECTION, CHUNK_SIZE);
        request.minResults = true;
        for (int i = 0; i < featuresInChunk; i++) {
          final XyzFeature feature = features[i];
          feature.setId(ids.get(pos++));
          feature.setGeometry(fg.newRandomLineString());
          request.add(EWriteOp.CREATE, feature);
        }
        out.printf(
            "\t[%3d] Execute request for chunk #%d with %d features\n",
            partitionId, chunk, featuresInChunk);
        out.flush();
        start = System.nanoTime();
        try (final Result result = session.execute(request)) {
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

  // gradle shadowJar -x test
  // scp /Users/alweber/Documents/github/xeus2001/xyz-hub/build/libs/naksha-2.0.10-all.jar alweber@$AWS_IP:.
  // java -Xmx32g -XX:+UseZGC -cp naksha-2.0.10-all.jar com.here.naksha.lib.psql.demo.BulkMain 1000000 1000 256
  public static void main(String... args) throws Exception {
    if (args.length < 2) {
      out.println(
          "Syntax: java -Xmx16g -XX:+UseZGC -cp naksha-2.0.10-all.jar com.here.naksha.lib.psql.demo.BulkMain {features} {chunkSize} [{partition}]");
      System.exit(1);
    }
    final int LIMIT = Math.max(1000, Math.min(1024 * 1024 * 1024, Integer.parseInt(args[0], 10)));
    CHUNK_SIZE = Math.max(100, Math.min(10_000, Integer.parseInt(args[1], 10)));
    if (args.length >= 3) {
      PARTITIONS = Math.max(1, Math.min(256, Integer.parseInt(args[2], 10)));
    } else {
      PARTITIONS = Math.max(1, Math.min(256, Runtime.getRuntime().availableProcessors()));
    }
    out.printf("Generate %d random features, in chunks of %d using %d partitions\n", LIMIT, CHUNK_SIZE, PARTITIONS);
    try (final PsqlStorage storage = new PsqlStorage(DB_URL)) {
      storage.setSocketTimeout(1, TimeUnit.MINUTES);
      out.println("Drop the schema " + storage.getSchema());
      storage.dropSchema();
      out.println("Create the schema " + storage.getSchema());
      storage.initStorage(new Params().pg_hint_plan(false).pg_stat_statements(true));
      out.println("Create the collection " + DB_COLLECTION);
      try (final PsqlWriteSession session = storage.newWriteSession(context, true)) {
        final WriteXyzCollections writeCollections = new WriteXyzCollections();
        writeCollections.add(EWriteOp.CREATE, new XyzCollection(DB_COLLECTION, true, false, true));
        try (final Result result = session.execute(writeCollections)) {
          session.commit(true);
        }
      }

      // Done, write features.
      //noinspection unchecked
      final Future<Long>[] results = (Future<Long>[]) new Future[PARTITIONS];
      //noinspection unchecked
      final List<@NotNull WriteXyzFeatures>[] requests = (List<WriteXyzFeatures>[]) new List[PARTITIONS];
      for (int i = 0; i < PARTITIONS; i++) {
        requests[i] = new ArrayList<>();
      }
      out.println("Generate the IDs");
      //noinspection unchecked
      final List<String>[] ids = (List<String>[]) new List[PARTITIONS];
      for (int i = 0; i < ids.length; i++) {
        ids[i] = new ArrayList<>();
      }
      for (int i = 0; i < LIMIT; i++) {
        final String id = RandomStringUtils.randomAlphanumeric(20);
        final int partitionId = PsqlHelper.partitionId(id) % PARTITIONS;
        ids[partitionId].add(id);
        if (i % 10000 == 0) {
          out.format("Generated %d ids ...\n", i);
        }
      }
      out.println("Done generating ids");
      out.println("Start writing the features");
      final long START = System.nanoTime();
      for (int i = 0; i < PARTITIONS; i++) {
        results[i] = new SimpleTask<Long>().start(BulkMain::writeFeatures, storage, i, ids[i]);
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
