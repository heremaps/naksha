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
package com.here.naksha.lib.core.models.storage;

import static com.here.naksha.lib.jbon.BigInt64Kt.BigInt64;
import static com.here.naksha.lib.jbon.BigInt64Kt.toLong;
import static com.here.naksha.lib.jbon.ConstantsKt.ACTION_CREATE;
import static com.here.naksha.lib.jbon.ConstantsKt.ACTION_DELETE;
import static com.here.naksha.lib.jbon.ConstantsKt.ACTION_UPDATE;
import static com.here.naksha.lib.jbon.ConstantsKt.newDataView;

import com.here.naksha.lib.core.models.geojson.coordinates.JTSHelper;
import com.here.naksha.lib.core.models.geojson.implementation.EXyzAction;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzGeometry;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzTags;
import com.here.naksha.lib.jbon.IMap;
import com.here.naksha.lib.jbon.JbBuilder;
import com.here.naksha.lib.jbon.JbDict;
import com.here.naksha.lib.jbon.JbFeature;
import com.here.naksha.lib.jbon.JbMap;
import com.here.naksha.lib.jbon.JvmEnv;
import com.here.naksha.lib.jbon.JvmMap;
import com.here.naksha.lib.jbon.XyzBuilder;
import com.here.naksha.lib.jbon.XyzNs;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The default codec for XYZ core library, this can simply be specialized.
 */
public class XyzCodec<FEATURE extends XyzFeature, SELF extends XyzCodec<FEATURE, SELF>>
    extends FeatureCodec<FEATURE, SELF> {

  XyzCodec(@NotNull Class<FEATURE> featureClass) {
    this.featureClass = featureClass;
  }

  private final @NotNull Class<FEATURE> featureClass;

  @NotNull
  @Override
  public final SELF decodeParts(boolean force) {
    if (!force && isDecoded) {
      return self();
    }
    if (feature == null) {
      throw new NullPointerException();
    }
    XyzGeometry xyzGeometry = feature.removeGeometry();

    id = feature.getId();
    final XyzNamespace xyz = feature.getProperties().getXyzNamespace();
    uuid = xyz.getUuid();

    if (xyzGeometry != null) {
      geometry = xyzGeometry.getJTSGeometry();
    } else {
      geometry = null;
    }
    wkb = null;

    IMap featureAsMap = JvmEnv.get().convert(feature, JvmMap.class);
    // TODO global dict
    // TODO what about features that need more than 64KB of buffer? newDataView should handle it?
    JbDict globalDict = null;
    JbBuilder builder = new JbBuilder(newDataView(65536), globalDict);
    featureJbon = builder.buildFeatureFromMap(featureAsMap);

    feature.setGeometry(xyzGeometry);

    decodeXyzNamespace(xyz, globalDict);
    decodeTags(xyz.getTags(), globalDict);

    isDecoded = true;
    return self();
  }

  public void decodeXyzNamespace(@NotNull XyzNamespace xyz, @Nullable JbDict globalDict) {
    XyzBuilder xyzBuilder = new XyzBuilder(newDataView(1024), globalDict);
    xyzNsJbon = xyzBuilder.buildXyzNs(
        BigInt64(xyz.getCreatedAt()),
        BigInt64(xyz.getUpdatedAt()),
        BigInt64(xyz.getTxn()),
        mapAction(xyz.getAction()),
        Math.toIntExact(xyz.getVersion()),
        BigInt64(xyz.getAuthorTime()),
        BigInt64(xyz.getExtend()),
        xyz.getPuuid(),
        xyz.getUuid(),
        xyz.getAppId(),
        xyz.getAuthor(),
        xyz.getCrid(),
        xyz.getGrid());
  }

  private void decodeTags(@Nullable List<@NotNull String> tags, @Nullable JbDict globalDict) {
    XyzBuilder xyzBuilder = new XyzBuilder(newDataView(512), globalDict);
    tagsJbon = null;
    if (tags != null && !tags.isEmpty()) {
      xyzBuilder.startTags();
      for (String tag : tags) {
        xyzBuilder.writeTag(tag);
      }
      tagsJbon = xyzBuilder.buildTags();
    }
  }

  private Integer mapAction(EXyzAction action) {
    if (action == EXyzAction.CREATE) {
      return ACTION_CREATE;
    } else if (action == EXyzAction.UPDATE) {
      return ACTION_UPDATE;
    } else if (action == EXyzAction.DELETE) {
      return ACTION_DELETE;
    } else if (action == null || action.isNull()) {
      return null;
    }
    throw new UnsupportedOperationException(String.format("Action type %s is not supported", action));
  }

  @Override
  public final @NotNull SELF encodeFeature(boolean force) {
    if (!force && isEncoded) {
      return self();
    }
    if (featureJbon == null) {
      return self();
    }
    feature = null;

    feature = getFeatureFromJbon();
    if (id != null) {
      feature.setId(id);
    }
    feature.setGeometry(JTSHelper.fromGeometry(getGeometry()));
    if (xyzNsJbon != null) {
      feature.getProperties().setXyzNamespace(getXyzNamespaceFromFromJbon());
    }
    isEncoded = true;
    return self();
  }

  @SuppressWarnings("unchecked")
  private FEATURE getFeatureFromJbon() {
    JbFeature jbFeature = new JbFeature().mapBytes(featureJbon, 0, featureJbon.length);
    Map<String, Object> featureAsMap = (Map<String, Object>)
        new JbMap().mapReader(jbFeature.getReader()).toIMap();
    return JvmEnv.get().convert(featureAsMap, featureClass);
  }

  private XyzNamespace getXyzNamespaceFromFromJbon() {
    XyzNs xyzNs = new XyzNs();
    xyzNs.mapBytes(xyzNsJbon, 0, xyzNsJbon.length);

    XyzNamespace retNs = new XyzNamespace();
    retNs.setUuid(xyzNs.uuid());
    retNs.setAction(xyzNs.actionAsString());
    retNs.setAuthor(xyzNs.author());
    retNs.setPuuid(xyzNs.puuid());
    retNs.setTxn(toLong(xyzNs.txn().getValue()));
    retNs.setCreatedAt(toLong(xyzNs.createdAt()));
    retNs.setUpdatedAt(toLong(xyzNs.updatedAt()));
    retNs.setExtend(toLong(xyzNs.extend()));
    retNs.setAppId(xyzNs.appId());
    retNs.setAuthorTime(toLong(xyzNs.authorTs()));
    retNs.setRealTimeUpdatedAt(toLong(xyzNs.updatedAt()));
    retNs.setVersion(xyzNs.version());
    retNs.setTags(getXyzTagsFromJbon(), false);

    return retNs;
  }

  private XyzTags getXyzTagsFromJbon() {
    if (tagsJbon == null) {
      return null;
    }
    com.here.naksha.lib.jbon.XyzTags xyzTags =
        new com.here.naksha.lib.jbon.XyzTags().mapBytes(tagsJbon, 0, tagsJbon.length);
    XyzTags tags = new XyzTags();
    tags.addAll(List.of(xyzTags.tagsArray()));
    return tags;
  }
}
