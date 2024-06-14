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

import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.storage.CodecError;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.models.storage.FeatureCodecFactory;
import com.here.naksha.lib.core.models.storage.ForwardCursor;
import com.here.naksha.lib.nak.Flags;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A result-cursor that is not thread-safe.
 *
 * @param <FEATURE> The feature type that the cursor returns.
 * @param <CODEC>   The codec type.
 */
public class PsqlCursor<FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>> extends ForwardCursor<FEATURE, CODEC> {

  private static final Logger log = LoggerFactory.getLogger(PsqlCursor.class);

  PsqlCursor(
      @NotNull FeatureCodecFactory<FEATURE, CODEC> codecFactory,
      @Nullable RequestedParams reqParams,
      @NotNull PostgresSession session,
      @NotNull Statement stmt,
      @NotNull ResultSet rs) {
    super(codecFactory);
    if (reqParams == null) {
      this.reqParams = new RequestedParams();
    } else {
      this.reqParams = reqParams;
    }
    cursor = new PostgresCursor(this, session, stmt, rs);
  }

  private final @NotNull RequestedParams reqParams;
  private final @NotNull PostgresCursor cursor;

  @Override
  protected boolean loadNextRow(@NotNull Row row) {
    final ResultSet rs = cursor.rs;
    try {
      if (rs.next()) {
        final String r_op = rs.getString(1);
        final String r_id = rs.getString(2);
        final byte[] r_xyz = rs.getBytes(3);
        final byte[] r_tags = rs.getBytes(4);
        final byte[] r_feature = rs.getBytes(5);
        final Integer r_flags = rs.getInt(6);
        final byte[] r_geo = rs.getBytes(7);
        final String r_err_no = rs.getString(8);
        final String r_err = rs.getString(9);

        int idx = rs.getRow() - 1;

        row.codec.setOp(r_op);
        row.codec.setId(r_id);
        row.codec.setXyzNsBytes(r_xyz);
        row.codec.setTagsBytes(defaultIfNull(r_tags, reqParams.tags, idx));
        row.codec.setGeometryBytes(defaultIfNull(r_geo, reqParams.geo, idx));
        row.codec.setFlags(new Flags(r_flags));
        row.codec.setFeatureBytes(defaultIfNull(r_feature, reqParams.features, idx));
        row.codec.setRawError(r_err);
        row.codec.setErr(mapToCodecError(r_err_no, r_err));
        row.valid = true;
        return true;
      }
      row.clear();
      return false;
    } catch (SQLException e) {
      throw unchecked(e);
    }
  }

  private byte[] defaultIfNull(byte[] value, byte[][] alt, int altIdx) {
    if (value == null && alt != null) {
      return alt[altIdx];
    }
    return value;
  }

  @Override
  public void close() {
    cursor.close();
  }

  private CodecError mapToCodecError(String r_err_no, String r_err) {
    CodecError codecError = null;
    if (r_err_no != null) {
      final XyzError xyzError = XyzErrorMapper.psqlCodeToXyzError(r_err_no);
      codecError = new CodecError(xyzError, r_err);
    }
    return codecError;
  }
}
