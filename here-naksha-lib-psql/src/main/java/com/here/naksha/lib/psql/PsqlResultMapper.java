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
package com.here.naksha.lib.psql;

import static com.here.naksha.lib.jbon.IMapKt.get;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import com.here.naksha.lib.core.models.storage.CodecError;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.models.storage.FeatureCodecFactory;
import com.here.naksha.lib.jbon.IMap;
import java.util.ArrayList;
import java.util.List;

public class PsqlResultMapper {

  public static <CODEC extends FeatureCodec<FEATURE, CODEC>, FEATURE> List<CODEC> mapRowToCodec(
      FeatureCodecFactory<FEATURE, CODEC> codecFactory, List<?> reqCodecs, List<IMap> rsRows) {
    List<CODEC> codecRows = new ArrayList<>(reqCodecs.size());
    for (int i = 0; i < rsRows.size(); i++) {
      CODEC codec = codecFactory.newInstance();
      IMap row = rsRows.get(i);
      CODEC reqCodec = (CODEC) reqCodecs.get(i);
      codec.setId(get(row, "id"));
      codec.setOp(get(row, "op"));
      codec.setXyzNsBytes(get(row, "xyz"));
      codec.setTagsBytes(defaultIfNull(get(row, "tags"), reqCodec.getTagsBytes()));
      codec.setGeometryBytes(defaultIfNull(get(row, "geo"), reqCodec.getGeometryBytes()));
      codec.setGeometryEncoding(defaultIfNull(get(row, "flags"), reqCodec.getGeometryEncoding()));
      codec.setFeatureBytes(defaultIfNull(get(row, "feature"), reqCodec.getFeatureBytes()));
      String errMsg = get(row, "err_msg");
      codec.setRawError(errMsg);
      String errNo = get(row, "err_no");
      if (errNo != null) codec.setErr(new CodecError(XyzErrorMapper.psqlCodeToXyzError(errNo), errMsg));
      codecRows.add(codec);
    }
    return codecRows;
  }
}
