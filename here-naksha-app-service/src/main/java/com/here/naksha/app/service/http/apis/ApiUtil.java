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
package com.here.naksha.app.service.http.apis;

import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.PRef;
import com.here.naksha.lib.core.models.storage.SOp;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApiUtil {

  public static @Nullable POp buildOperationForTagList(final @Nullable List<String> tagList) {
    final String OR_CHAR = ",";
    final String AND_CHAR = "+";
    final List<POp> allTagOpList = new ArrayList<>();

    if (tagList == null || tagList.isEmpty()) {
      return null;
    }
    for (final String encodedTagParam : tagList) {
      final String tagParam = URLDecoder.decode(encodedTagParam, StandardCharsets.UTF_8);
      if (tagParam == null || tagParam.isEmpty()) continue;
      final String op = tagParam.contains(OR_CHAR) ? OR_CHAR : (tagParam.contains(AND_CHAR) ? AND_CHAR : null);
      final POp crtTagOp;
      if (op == null) {
        crtTagOp = POp.exists(PRef.tag(XyzNamespace.normalizeTag(tagParam)));
      } else {
        final String[] tags = tagParam.split(OR_CHAR.equals(op) ? op : "\\" + op);
        final List<POp> crtTagOpList = Arrays.stream(tags)
            .map(tag -> POp.exists(PRef.tag(XyzNamespace.normalizeTag(tag))))
            .toList();
        crtTagOp = OR_CHAR.equals(op)
            ? POp.or(crtTagOpList.toArray(POp[]::new))
            : POp.and(crtTagOpList.toArray(POp[]::new));
      }
      allTagOpList.add(crtTagOp);
    }
    final POp[] allTagOpArr = allTagOpList.toArray(POp[]::new);
    return (allTagOpArr.length > 1) ? POp.or(allTagOpArr) : allTagOpArr[0];
  }

  public static @NotNull SOp buildOperationForBBox(
      final double west, final double south, final double east, final double north) {
    return SOp.intersects(RequestHelper.createBBoxEnvelope(west, south, east, north));
  }
}
