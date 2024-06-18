/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.psql;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.jbon.BigInt64Kt.toLong;
import static com.here.naksha.lib.jbon.IMapKt.get;
import static com.here.naksha.lib.psql.XyzErrorMapper.psqlCodeToXyzError;
import static com.here.naksha.lib.psql.sql.SqlGeometryTransformationResolver.addTransformation;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.StorageLockException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.models.storage.HeapCacheCursor;
import com.here.naksha.lib.core.models.storage.Notification;
import com.here.naksha.lib.core.models.storage.OpType;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.ReadRequest;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SOp;
import com.here.naksha.lib.core.models.storage.SOpType;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import com.here.naksha.lib.core.models.storage.WriteCollections;
import com.here.naksha.lib.core.models.storage.WriteFeatures;
import com.here.naksha.lib.core.models.storage.WriteRequest;
import com.here.naksha.lib.core.models.storage.XyzCollectionCodec;
import com.here.naksha.lib.core.models.storage.XyzCollectionCodecFactory;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodec;
import com.here.naksha.lib.core.models.storage.XyzFeatureCodecFactory;
import com.here.naksha.lib.core.storage.IStorageLock;
import com.here.naksha.lib.core.util.ClosableChildResource;
import com.here.naksha.lib.core.util.IndexHelper;
import com.here.naksha.lib.core.util.json.Json;
import naksha.jbon.IMap;
import naksha.jbon.JbSession;
import naksha.jbon.NakshaTxn;
import naksha.jbon.NakshaUuid;
import com.here.naksha.lib.plv8.JvmPlv8Env;
import com.here.naksha.lib.plv8.JvmPlv8Sql;
import com.here.naksha.lib.plv8.JvmPlv8Table;
import com.here.naksha.lib.plv8.NakshaSession;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A PostgresQL session being backed by the PostgresQL connection. It keeps track of all open cursors as resource children and guarantees
 * that all cursors are closed before the underlying connection is closed.
 */
@SuppressWarnings("DuplicatedCode")
final class PostgresSession extends ClosableChildResource<PostgresStorage> {

  private static final Logger log = LoggerFactory.getLogger(PostgresSession.class);

  // There are two facades: PsqlReadSession and PsqlWriteSession.
  // They only differ in that they set the last parameter to true or false.
  PostgresSession(
      @NotNull PsqlSession proxy,
      @NotNull PostgresStorage storage,
      @NotNull NakshaContext context,
      @NotNull PsqlConnection psqlConnection) {
    super(proxy, storage);
    this.context = context;
    this.psqlConnection = psqlConnection;
    this.readOnly = psqlConnection.postgresConnection.parent().config.readOnly;
    this.sql = new SQL();
    this.fetchSize = storage.getFetchSize();
    this.stmtTimeoutMillis = storage.getLockTimeout(MILLISECONDS);
    this.lockTimeoutMillis = storage.getLockTimeout(MILLISECONDS);

    JvmPlv8Sql sql = new JvmPlv8Sql(psqlConnection);
    this.nakshaSession = new NakshaSession(
        sql,
        storage.getSchema(),
        storage.storageId,
        storage.getAppName(),
        context.getStreamId(),
        context.getAppId(),
        context.getAuthor());
    JbSession.Companion.getThreadLocal().set(nakshaSession);
  }

  final @NotNull NakshaSession nakshaSession;

  /**
   * The context to be used.
   */
  final @NotNull NakshaContext context;

  int fetchSize;
  long stmtTimeoutMillis;
  long lockTimeoutMillis;

  final @NotNull PsqlConnection psqlConnection;
  final boolean readOnly;
  private final @NotNull SQL sql;

  @Override
  protected void destruct() {
    try {
      psqlConnection.close();
    } catch (Exception e) {
      log.atInfo()
          .setMessage("Failed to close PostgresQL connection")
          .setCause(e)
          .log();
    }
  }

  int getFetchSize() {
    return fetchSize;
  }

  void setFetchSize(int size) {
    if (size <= 1) {
      throw new IllegalArgumentException("The fetchSize must be greater than zero");
    }
    this.fetchSize = size;
  }

  long getStatementTimeout(@NotNull TimeUnit timeUnit) {
    return timeUnit.convert(stmtTimeoutMillis, MILLISECONDS);
  }

  void setStatementTimeout(long timeout, @NotNull TimeUnit timeUnit) throws SQLException {
    if (timeout < 0) {
      throw new IllegalArgumentException("The timeout must be greater/equal zero");
    }
    final long stmtTimeoutMillis = MILLISECONDS.convert(timeout, timeUnit);
    if (stmtTimeoutMillis != this.stmtTimeoutMillis) {
      this.stmtTimeoutMillis = stmtTimeoutMillis;
      executeStatement(sql().add("SET SESSION statement_timeout TO ")
          .add(stmtTimeoutMillis)
          .add(";\n"));
    }
  }

  long getLockTimeout(@NotNull TimeUnit timeUnit) {
    return timeUnit.convert(lockTimeoutMillis, MILLISECONDS);
  }

  void setLockTimeout(long timeout, @NotNull TimeUnit timeUnit) throws SQLException {
    if (timeout < 0) {
      throw new IllegalArgumentException("The timeout must be greater/equal zero");
    }
    final long lockTimeoutMillis = MILLISECONDS.convert(timeout, timeUnit);
    if (this.lockTimeoutMillis != lockTimeoutMillis) {
      this.lockTimeoutMillis = lockTimeoutMillis;
      executeStatement(sql().add("SET SESSION lock_timeout TO ")
          .add(lockTimeoutMillis)
          .add(";\n"));
    }
  }

  @NotNull
  SQL sql() {
    sql.setLength(0);
    return sql;
  }

  void executeStatement(@NotNull CharSequence query) throws SQLException {
    try (final Statement stmt = psqlConnection.createStatement()) {
      stmt.execute(query.toString());
    }
  }

  @SuppressWarnings("SqlSourceToSinkFlow")
  @NotNull
  PreparedStatement prepareStatement(@NotNull CharSequence query) {
    try {
      final PreparedStatement stmt = psqlConnection.prepareStatement(
          query.toString(),
          ResultSet.TYPE_FORWARD_ONLY,
          ResultSet.CONCUR_READ_ONLY,
          ResultSet.CLOSE_CURSORS_AT_COMMIT);
      stmt.setFetchSize(fetchSize);
      return stmt;
    } catch (SQLException e) {
      throw unchecked(e);
    }
  }

  void commit(boolean autoCloseCursors) throws SQLException {
    // TODO: Apply autoCloseCursors
    psqlConnection.commit();
    clearSession();
  }

  void rollback(boolean autoCloseCursors) throws SQLException {
    // TODO: Apply autoCloseCursors
    psqlConnection.rollback();
    clearSession();
  }

  void close(boolean autoCloseCursors) {
    // TODO: Apply autoCloseCursors
    JvmPlv8Env.get().endSession();
    psqlConnection.close();
  }

  private void clearSession() throws SQLException {
    nakshaSession.clear();
    psqlConnection.commit();
  }

  private static void assure3d(@NotNull Coordinate @NotNull [] coords) {
    for (final @NotNull Coordinate coord : coords) {
      if (coord.z != coord.z) { // if coord.z is NaN
        coord.z = 0;
      }
    }
  }

  @NotNull
  Result process(@NotNull Notification<?> notification) {
    return new ErrorResult(XyzError.NOT_IMPLEMENTED, "process");
  }

  private static void addSpatialQuery(@NotNull SQL sql, @NotNull SOp spatialOp, @NotNull List<byte[]> wkbs) {
    final OpType op = spatialOp.op();
    if (SOpType.AND == op || SOpType.OR == op || SOpType.NOT == op) {
      final List<@NotNull SOp> children = spatialOp.children();
      if (children == null || children.size() == 0) {
        return;
      }
      final String op_literal;
      if (SOpType.AND == op) {
        op_literal = " AND";
      } else if (SOpType.OR == op) {
        op_literal = " OR";
      } else {
        op_literal = " NOT";
      }
      boolean first = true;
      sql.add('(');
      for (final @NotNull SOp child : children) {
        if (first) {
          first = false;
        } else {
          sql.add(op_literal);
        }
        addSpatialQuery(sql, child, wkbs);
      }
      sql.add(")");
    } else if (SOpType.INTERSECTS == op) {
      final Geometry geometry = spatialOp.getGeometry();
      if (geometry == null) {
        throw new IllegalArgumentException("Missing geometry");
      }
      SQL variableTransformed =
          addTransformation(spatialOp.getTransformation(), "ST_Force3D(naksha_geometry_in_type(3::int2,?))");
      sql.add(" ST_Intersects(naksha_geometry(flags,geo), ")
          .add(variableTransformed)
          .add(")");
      try (final Json jp = Json.get()) {
        final byte[] wkb = jp.twkbWriter.write(geometry);
        wkbs.add(wkb);
      }
    } else {
      throw new IllegalArgumentException("Unknown operation: " + op);
    }
  }

  private static void addJsonPath(@NotNull SQL sql, @NotNull PRef pRef, int end, boolean text, boolean nullif) {
    if (nullif) {
      sql.add("nullif(");
    }
    // search by indexed function for all supported properties
    if (pRef.equals(PRef.id())) {
      sql.add("id");
    } else if (pRef.equals(PRef.txn())) {
      sql.add("txn");
    } else if (pRef.equals(PRef.txn_next())) {
      sql.add("txn_next");
    } else if (pRef.equals(PRef.uuid())) {
      sql.add("uid");
    } else if (pRef.equals(PRef.app_id())) {
      sql.add("author");
    } else if (pRef.equals(PRef.grid())) {
      sql.add("geo_grid");
    } else if (pRef.getTagName() != null) {
      sql.add("tags_to_jsonb(tags)");
    } else {
      // not indexed access
      sql.add("(");
      sql.add("feature_to_jsonb(naksha_feature(feature,flags))");
      List<@NotNull String> path = pRef.getPath();
      final int last = end - 1;
      for (int i = 0; i < end; i++) {
        final String pname = path.get(i);
        sql.add(i == last && text ? "->>" : "->");
        sql.addLiteral(pname);
      }
      sql.add(')');
    }
    if (text) {
      sql.add(" COLLATE \"C\" ");
    }
    if (nullif) {
      sql.add(",null)");
    }
  }

  private static void addOp( //
      @NotNull SQL sql, //
      @NotNull List<Object> parameter, //
      final @NotNull PRef pRef, //
      @NotNull OpType opType, //
      @Nullable Object value //
      ) {
    if (value == null) {
      throw new IllegalArgumentException("Invalid value NULL for op: " + opType);
    }
    if (!(opType instanceof POpType)) {
      throw new IllegalArgumentException("Operation not supported: " + opType);
    }
    final POpType op = (POpType) opType;
    final String opString = op.op();
    if (opString == null) {
      throw new IllegalArgumentException("Operation not supported: " + op);
    }
    final int prefPathSize = pRef.getPath().size();
    if (op == POpType.CONTAINS) {
      addJsonPath(sql, pRef, prefPathSize, false, false);
      sql.add(" ").add(opString).add(" ?::jsonb");
      parameter.add(toJsonb(value));
    } else if (value instanceof CharSequence) {
      addJsonPath(sql, pRef, prefPathSize, true, false);
      sql.add("::text ").add(opString).add(" ?");
      parameter.add(value);
    } else if (value instanceof Double) {
      addJsonPath(sql, pRef, prefPathSize, false, false);
      sql.add("::double precision ").add(opString).add(" ?");
      parameter.add(value);
    } else if (value instanceof Float) {
      addJsonPath(sql, pRef, prefPathSize, false, false);
      sql.add("::double precision ").add(opString).add(" ?");
      parameter.add(((Number) value).doubleValue());
    } else if (value instanceof Long) {
      addJsonPath(sql, pRef, prefPathSize, false, false);
      sql.add("::int8 ").add(opString).add(" ?");
      parameter.add(value);
    } else if (value instanceof Number) {
      addJsonPath(sql, pRef, prefPathSize, false, false);
      sql.add("::int8 ").add(opString).add(" ?");
      parameter.add(((Number) value).longValue());
    } else if (value instanceof Boolean) {
      addJsonPath(sql, pRef, prefPathSize, false, false);
      sql.add("::bool ").add(opString).add(" ?");
      parameter.add(value);
    } else {
      throw new IllegalArgumentException(
          "Unknown value type: " + (value.getClass().getName()));
    }
  }

  private static void addPropertyQuery(
      @NotNull SQL sql, @NotNull POp propertyOp, @NotNull List<Object> parameter, boolean isHstQuery) {
    final OpType op = propertyOp.op();
    if (POpType.AND == op || POpType.OR == op || POpType.NOT == op) {
      final List<@NotNull POp> children = propertyOp.children();
      if (children == null || children.size() == 0) {
        return;
      }
      final String op_literal;
      if (POpType.AND == op) {
        op_literal = " AND";
      } else if (POpType.OR == op) {
        op_literal = " OR";
      } else {
        op_literal = "";
        sql.add(" NOT");
      }
      boolean first = true;
      sql.add('(');
      for (final @NotNull POp child : children) {
        if (first) {
          first = false;
        } else {
          sql.add(op_literal);
        }
        addPropertyQuery(sql, child, parameter, isHstQuery);
      }
      sql.add(")");
      return;
    }
    sql.add(' ');
    final PRef pref = propertyOp.getPropertyRef();
    assert pref != null;
    final List<@NotNull String> path = pref.getPath();
    if (pref.getTagName() != null) {
      if (op != POpType.EXISTS) {
        throw new IllegalArgumentException("Tags do only support EXISTS operation, not " + op);
      }
      addJsonPath(sql, pref, path.size(), false, false);
      sql.add(" ?? ?");
      parameter.add(pref.getTagName());
      return;
    }
    if (op == POpType.EXISTS) {
      addJsonPath(sql, pref, path.size() - 1, false, false);
      sql.add(" ?? ?");
      parameter.add(path.get(path.size() - 1));
      return;
    }
    if (op == POpType.NULL) {
      addJsonPath(sql, pref, path.size(), false, false);
      sql.add(" = 'null'");
      return;
    }
    if (op == POpType.NOT_NULL) {
      addJsonPath(sql, pref, path.size(), false, false);
      sql.add(" != 'null'");
      return;
    }
    final Object value = propertyOp.getValue();
    if (op == POpType.STARTS_WITH) {
      if (value instanceof String) {
        String text = (String) value;
        addJsonPath(sql, pref, path.size(), true, false);
        sql.add(" LIKE ?");
        parameter.add(text + '%');
        return;
      }
      throw new IllegalArgumentException("STARTS_WITH operator requires a string as value");
    }
    if (op == POpType.EQ && pref == PRef.txn()) {
      sql.add("(");
      addJsonPath(sql, pref, path.size(), false, true);
      sql.add("::int8 <= ?");
      if (!(value instanceof Number)) {
        throw new IllegalArgumentException("Value must be a number");
      }
      final Long txn = ((Number) value).longValue();
      parameter.add(txn);
      if (isHstQuery) {
        sql.add(" AND ");
        addPropertyQuery(sql, POp.gt(PRef.txn_next(), txn), parameter, true);
      }
      sql.add(")");
      return;
    }
    if (pref == PRef.uuid()) {
      sql.add("(");
      NakshaUuid nakshaUuid =
          NakshaUuid.Companion.fromString(propertyOp.getValue().toString());
      addOp(sql, parameter, pref, op, nakshaUuid.getUid());
      sql.add(" AND ");
      NakshaTxn nakshatxn = NakshaTxn.Companion.of(
          nakshaUuid.getYear(), nakshaUuid.getMonth(), nakshaUuid.getDay(), nakshaUuid.getSeq());
      addOp(sql, parameter, PRef.txn(), op, toLong(nakshatxn.getValue()));
      sql.add(")");
      return;
    }
    addOp(sql, parameter, pref, op, value);
  }

  private static PGobject toJsonb(Object value) {
    try (final Json jp = Json.get()) {
      final PGobject jsonb = new PGobject();
      jsonb.setType("jsonb");
      // TODO: Remove this, we do not want to guess!
      if (value instanceof String && Json.mightBeJson((String) value)) {
        // it's already a json - .writeValueAsString would add double quoting
        jsonb.setValue((String) value);
      } else {
        jsonb.setValue(jp.writer().writeValueAsString(value));
      }
      return jsonb;
    } catch (SQLException | JsonProcessingException e) {
      throw unchecked(e);
    }
  }

  private SQL prepareQuery(String collection, String spatial_where, String props_where, Long limit) {
    final SQL query = new SQL();
    query.add("(SELECT 'READ',\n" + "id,\n")
        .add(
            "row_to_ns(created_at,updated_at,txn,action,version,author_ts,uid,app_id,author,geo_grid,puid,ptxn,")
        .addLiteral(collection.replaceFirst("$hst", "")) // uuid should not to refer to _hst table
        .add("::text),\n" + "tags,\n" + "feature,\n" + "flags,\n" + "geo,\n" + "null,\n" + "null FROM ")
        .addIdent(collection);
    if (spatial_where.length() > 0 || props_where.length() > 0) {
      query.add(" WHERE");
      if (spatial_where.length() > 0) {
        query.add(spatial_where);
        if (props_where.length() > 0) {
          query.add(" AND");
        }
      }
      if (props_where.length() > 0) {
        query.add(props_where);
      }
    }
    if (limit != null) {
      query.add(" LIMIT ").add(limit);
    }
    query.add(")");
    return query;
  }

  private int fillStatementWithParams(
      @NotNull PreparedStatement stmt,
      @NotNull List<byte[]> wkbs,
      @NotNull List<Object> parameters,
      int startParamIdx,
      int repeatCount)
      throws SQLException {
    int i = startParamIdx;
    for (int repetition = 1; repetition <= repeatCount; repetition++) {
      for (final byte[] wkb : wkbs) {
        stmt.setBytes(i++, wkb);
      }
      for (final Object value : parameters) {
        if (value == null) {
          stmt.setString(i++, null);
        } else if (value instanceof PGobject) {
          stmt.setObject(i++, value);
        } else if (value instanceof String) {
          stmt.setString(i++, (String) value);
        } else if (value instanceof Double) {
          stmt.setDouble(i++, (Double) value);
        } else if (value instanceof Float) {
          stmt.setFloat(i++, (Float) value);
        } else if (value instanceof Long) {
          stmt.setLong(i++, (Long) value);
        } else if (value instanceof Integer) {
          stmt.setInt(i++, (Integer) value);
        } else if (value instanceof Short) {
          stmt.setShort(i++, (Short) value);
        } else if (value instanceof Boolean) {
          stmt.setBoolean(i++, (Boolean) value);
        } else {
          throw new IllegalArgumentException("Invalid value at index " + i + ": " + value);
        }
      }
    }
    return i;
  }

  @NotNull
  Result executeRead(@NotNull ReadRequest<?> readRequest) {
    if (readRequest instanceof ReadFeatures) {
      final ReadFeatures readFeatures = (ReadFeatures) readRequest;
      final List<@NotNull String> collections = readFeatures.getCollections();
      if (collections.size() == 0) {
        return new PsqlSuccess(null);
      }
      final SQL sql = sql();
      final ArrayList<byte[]> wkbs = new ArrayList<>();
      final ArrayList<Object> parameters = new ArrayList<>();
      final ArrayList<Object> parametersHst = new ArrayList<>();
      SOp spatialOp = readFeatures.getSpatialOp();
      if (spatialOp != null) {
        addSpatialQuery(sql, spatialOp, wkbs);
      }
      final String spatial_where = sql.toString();
      sql.setLength(0);
      POp propertyOp = readFeatures.getPropertyOp();
      if (propertyOp != null) {
        addPropertyQuery(sql, propertyOp, parameters, false);
      }
      final String props_where = sql.toString();
      sql.setLength(0);
      boolean first = true;
      for (final String collection : collections) {
        if (first) {
          first = false;
        } else {
          sql.add(" UNION ALL ");
        }
        SQL headQuery = prepareQuery(collection, spatial_where, props_where, readFeatures.getLimit());
        sql.add(headQuery);
        if (readFeatures.isReturnAllVersions()) {
          sql.add(" UNION ALL ");
          SQL hstSql = prepareHstSql(collection, propertyOp, parametersHst, spatial_where, readFeatures);
          sql.add(hstSql);
        }
      }
      final String query = sql.toString();
      final PreparedStatement stmt = prepareStatement(query);
      try {
        int lastParamIdx = fillStatementWithParams(stmt, wkbs, parameters, 1, collections.size());
        if (readFeatures.isReturnAllVersions()) {
          fillStatementWithParams(stmt, wkbs, parametersHst, lastParamIdx, 1);
        }
        final ResultSet rs = stmt.executeQuery();
        final PsqlCursor<XyzFeature, XyzFeatureCodec> cursor =
            new PsqlCursor<>(XyzFeatureCodecFactory.get(), null, this, stmt, rs);
        return new PsqlSuccess(cursor);
      } catch (SQLException e) {
        try {
          stmt.close();
        } catch (SQLException ce) {
          log.atInfo()
              .setMessage("Failed to close statement")
              .setCause(ce)
              .log();
        }
        throw unchecked(e);
      }
    }
    return new ErrorResult(XyzError.NOT_IMPLEMENTED, "executeRead");
  }

  private SQL prepareHstSql(
      String collection,
      POp propertyOp,
      ArrayList<Object> parametersHst,
      String spatial_where,
      ReadFeatures readFeatures) {
    String historyCollection = collection + "$hst";
    SQL hst_props_where = new SQL();
    if (propertyOp != null) {
      addPropertyQuery(hst_props_where, propertyOp, parametersHst, true);
    }
    return prepareQuery(historyCollection, spatial_where, hst_props_where.toString(), readFeatures.getLimit());
  }

  @NotNull
  <FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>> Result executeWrite(
      @NotNull WriteRequest<FEATURE, CODEC, ?> writeRequest) {
    final long startTime = System.currentTimeMillis();
    String status = "OK";
    String method = "";
    if (writeRequest instanceof WriteCollections) {
      final PreparedStatement stmt = prepareStatement(
          "SELECT op, id, xyz, tags, feature, flags, geo, err_no, err_msg FROM naksha_write_collections(?,?,?,?,?);\n");
      final int SIZE = writeRequest.features.size();
      try {
        final List<@NotNull CODEC> features = writeRequest.features;
        final byte[][] reqOps = new byte[SIZE][];
        final byte[][] reqFeatures = new byte[SIZE][];
        final Integer[] reqFlags = new Integer[SIZE];
        final byte[][] reqGeo = new byte[SIZE][];
        final byte[][] reqTags = new byte[SIZE][];
        for (int i = 0; i < SIZE; i++) {
          final CODEC codec = features.get(i);
          codec.decodeParts(false);
          reqOps[i] = codec.getXyzOp();
          reqFeatures[i] = codec.getFeatureBytes();
          reqGeo[i] = codec.getGeometryBytes();
          reqFlags[i] = codec.getCombinedFlags();
          reqTags[i] = codec.getTagsBytes();
          method = codec.getOp();
        }
        stmt.setArray(1, psqlConnection.createArrayOf("bytea", reqOps));
        stmt.setArray(2, psqlConnection.createArrayOf("bytea", reqFeatures));
        stmt.setArray(3, psqlConnection.createArrayOf("int2", reqFlags));
        stmt.setArray(4, psqlConnection.createArrayOf("bytea", reqGeo));
        stmt.setArray(5, psqlConnection.createArrayOf("bytea", reqTags));

        JvmPlv8Table table =
            (JvmPlv8Table) nakshaSession.writeCollections(reqOps, reqFeatures, reqFlags, reqGeo, reqTags);
        ArrayList<IMap> rows = table.getRows();
        List<XyzCollectionCodec> codecRows = PsqlResultMapper.mapRowToCodec(
            XyzCollectionCodecFactory.get(), features, rows, nakshaSession.getSql());
        return new PsqlSuccess(new HeapCacheCursor<>(XyzCollectionCodecFactory.get(), codecRows, null), null);
      } catch (Throwable e) {
        try {
          status = "NOK";
          stmt.close();
        } catch (Throwable ce) {
          log.atInfo()
              .setMessage("Failed to close statement")
              .setCause(ce)
              .log();
        }
        throw unchecked(e);
      } finally {
        log.info(
            "[Storage Request stats => type,storageId,host,method,ftype,fCnt,collectionId,status,timeTakenMs] - StorageReqStats {} {} {} {} {} {} {} {} {}",
            "PsqlStorage",
            parent().storageId,
            psqlConnection.postgresConnection.parent().config.host,
            method,
            "Collection",
            SIZE,
            "-", // collectionId skipped for now
            status,
            System.currentTimeMillis() - startTime);
      }
    }
    if (writeRequest instanceof WriteFeatures<?, ?, ?>) {
      final WriteFeatures<?, ?, ?> writeFeatures = (WriteFeatures<?, ?, ?>) writeRequest;
      final int SIZE = writeRequest.features.size();
      final String collection_id = writeFeatures.getCollectionId();
      try {
        // new array list, so we don't modify original order
        final List<@NotNull CODEC> features = new ArrayList<>(writeRequest.features);
        features.forEach(codec -> codec.decodeParts(false));
        final Map<String, Integer> originalFeaturesOrder =
            IndexHelper.createKeyIndexMap(features, CODEC::getId);
        // sort to avoid deadlock
        features.sort(comparing(FeatureCodec::getId));
        final byte[][] op_arr = new byte[SIZE][];
        final byte[][] feature_arr = new byte[SIZE][];
        final Integer[] flags_arr = new Integer[SIZE];
        final byte[][] geo_arr = new byte[SIZE][];
        final byte[][] tags_arr = new byte[SIZE][];

        for (int i = 0; i < SIZE; i++) {
          final CODEC codec = features.get(i);
          op_arr[i] = codec.getXyzOp();
          feature_arr[i] = codec.getFeatureBytes();
          flags_arr[i] = codec.getCombinedFlags();
          geo_arr[i] = codec.getGeometryBytes();
          tags_arr[i] = codec.getTagsBytes();
          method = codec.getOp();
        }
        JvmPlv8Table table = (JvmPlv8Table) nakshaSession.writeFeatures(
            collection_id, op_arr, feature_arr, flags_arr, geo_arr, tags_arr, false);
        ArrayList<IMap> rows = table.getRows();
        XyzFeatureCodecFactory codecFactory = XyzFeatureCodecFactory.get();
        List<XyzFeatureCodec> codecRows =
            PsqlResultMapper.mapRowToCodec(codecFactory, features, rows, nakshaSession.getSql());
        HeapCacheCursor<XyzFeature, XyzFeatureCodec> cursor =
            new HeapCacheCursor<>(codecFactory, codecRows, originalFeaturesOrder);

        if (!codecRows.isEmpty() && codecRows.get(0).hasError()) {
          XyzFeatureCodec firstRow = codecRows.get(0);
          return new PsqlError(firstRow.getError().err, firstRow.getError().msg);
        }
        return new PsqlSuccess(cursor, originalFeaturesOrder);
      } catch (Throwable e) {
        status = "NOK";
        throw unchecked(e);
      } finally {
        log.info(
            "[Storage Request stats => type,storageId,host,method,ftype,fCnt,collectionId,status,timeTakenMs] - StorageReqStats {} {} {} {} {} {} {} {} {}",
            "PsqlStorage",
            parent().storageId,
            psqlConnection.postgresConnection.parent().config.host,
            method,
            "Feature",
            SIZE,
            collection_id,
            status,
            System.currentTimeMillis() - startTime);
      }
    }
    return new ErrorResult(XyzError.NOT_IMPLEMENTED, "The supplied write-request is not yet implemented");
  }

  @NotNull
  <FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>> Result executeBulkWrite(
      @NotNull WriteRequest<FEATURE, CODEC, ?> writeRequest) {
    final long startTime = System.currentTimeMillis();
    String status = "OK";
    String method = "";
    if (writeRequest instanceof WriteFeatures<?, ?, ?>) {
      final WriteFeatures<?, ?, ?> writeFeatures = (WriteFeatures<?, ?, ?>) writeRequest;
      final int SIZE = writeRequest.features.size();
      final String collection_id = writeFeatures.getCollectionId();
      try {
        // new array list, so we don't modify original order
        final List<@NotNull CODEC> features = new ArrayList<>(writeRequest.features);
        features.forEach(codec -> codec.decodeParts(false));
        // partition_id
        final byte[][] op_arr = new byte[SIZE][];
        final byte[][] feature_arr = new byte[SIZE][];
        final Integer[] flags_arr = new Integer[SIZE];
        final byte[][] geo_arr = new byte[SIZE][];
        final byte[][] tags_arr = new byte[SIZE][];

        for (int i = 0; i < SIZE; i++) {
          final CODEC codec = features.get(i);
          op_arr[i] = codec.getXyzOp();
          feature_arr[i] = codec.getFeatureBytes();
          flags_arr[i] = codec.getCombinedFlags();
          geo_arr[i] = codec.getGeometryBytes();
          tags_arr[i] = codec.getTagsBytes();
          method = codec.getOp();
        }
        JvmPlv8Table table = (JvmPlv8Table) nakshaSession.writeFeatures(
            collection_id, op_arr, feature_arr, flags_arr, geo_arr, tags_arr, true);
        ArrayList<IMap> rows = table.getRows();
        if (!rows.isEmpty()) {
          IMap err = rows.get(0);
          return new PsqlError(psqlCodeToXyzError(get(err, "err_no")), get(err, "err_msg"));
        } else {
          return new SuccessResult();
        }
      } catch (Throwable e) {
        throw unchecked(e);
      } finally {
        log.info(
            "[Storage Request stats => type,storageId,host,method,ftype,fCnt,collectionId,status,timeTakenMs] - StorageReqStats {} {} {} {} {} {} {} {} {}",
            "PsqlStorage",
            parent().storageId,
            psqlConnection.postgresConnection.parent().config.host,
            method,
            "Feature",
            SIZE,
            collection_id,
            status,
            System.currentTimeMillis() - startTime);
      }
    }
    return new ErrorResult(XyzError.NOT_IMPLEMENTED, "The supplied write-request is not yet implemented");
  }

  @NotNull
  IStorageLock lockFeature(
      @NotNull String collectionId, @NotNull String featureId, long timeout, @NotNull TimeUnit timeUnit)
      throws StorageLockException {
    throw new StorageLockException("Unsupported operation");
  }

  @NotNull
  IStorageLock lockStorage(@NotNull String lockId, long timeout, @NotNull TimeUnit timeUnit)
      throws StorageLockException {
    throw new StorageLockException("Unsupported operation");
  }
}
