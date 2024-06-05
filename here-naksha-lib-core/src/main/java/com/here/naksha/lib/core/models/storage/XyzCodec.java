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
package com.here.naksha.lib.core.models.storage;

import static com.here.naksha.lib.jbon.BigInt64Kt.toLong;
import static com.here.naksha.lib.jbon.ConstantsKt.newDataView;

import com.here.naksha.lib.core.models.geojson.coordinates.JTSHelper;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzGeometry;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzTags;
import com.here.naksha.lib.jbon.IMap;
import com.here.naksha.lib.jbon.JbBuilder;
import com.here.naksha.lib.jbon.JbDict;
import com.here.naksha.lib.jbon.JbDictManager;
import com.here.naksha.lib.jbon.JvmEnv;
import com.here.naksha.lib.jbon.JvmMap;
import com.here.naksha.lib.jbon.XyzBuilder;
import com.here.naksha.lib.jbon.XyzNs;
import com.here.naksha.lib.nak.Flags;
import java.util.List;
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

  @Override
  protected Flags getDefaultFlags() {
    Flags defaultFlags = new Flags();
    defaultFlags.setGeometryEncoding(GEO_TYPE_TWKB);
    return defaultFlags;
  }

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

    if (id == null) {
      id = feature.getId();
    }
    final XyzNamespace xyz = feature.getProperties().getXyzNamespace();
    uuid = xyz.getUuid();

    if (xyzGeometry != null) {
      geometry = xyzGeometry.getJTSGeometry();
    } else {
      geometry = null;
    }
    geometryBytes = null;

    // TODO global dict
    // TODO what about features that need more than 64KB of buffer? newDataView should handle it?
    JbDict globalDict = null;
    decodeXyzOp(globalDict);
    decodeTags(xyz.getTags(), globalDict);

    // we don't need xyzNamespace in feature bytea
    feature.getProperties().setXyzNamespace(new XyzNamespace());

    IMap featureAsMap = JvmEnv.get().convert(feature, JvmMap.class);
    JbBuilder builder = new JbBuilder(newDataView(65536), globalDict);
    featureBytes = builder.buildFeatureFromMap(featureAsMap);

    feature.getProperties().setXyzNamespace(xyz);
    feature.setGeometry(xyzGeometry);

    isDecoded = true;
    return self();
  }

  private void decodeTags(@Nullable List<@NotNull String> tags, @Nullable JbDict globalDict) {
    XyzBuilder xyzBuilder = new XyzBuilder(newDataView(512), globalDict);
    tagsBytes = null;
    if (tags != null && !tags.isEmpty()) {
      xyzBuilder.startTags();
      for (String tag : tags) {
        xyzBuilder.writeTag(tag);
      }
      tagsBytes = xyzBuilder.buildTags();
    }
  }

  @Override
  public final @NotNull SELF encodeFeature(boolean force) {
    if (!force && isEncoded) {
      return self();
    }
    if (featureBytes == null) {
      return self();
    }
    feature = getFeatureFromJbon(featureClass);
    if (id != null) {
      feature.setId(id);
    }
    feature.setGeometry(JTSHelper.fromGeometry(getGeometry()));
    if (xyzNsBytes != null) {
      XyzNamespace xyzNs = getXyzNamespaceFromFromJbon();
      feature.getProperties().setXyzNamespace(xyzNs);
      if (uuid == null) {
        uuid = xyzNs.getUuid();
      }
    }
    isEncoded = true;
    return self();
  }

  private XyzNamespace getXyzNamespaceFromFromJbon() {
    XyzNs xyzNs = new XyzNs();
    xyzNs.mapBytes(xyzNsBytes, 0, xyzNsBytes.length);

    XyzNamespace retNs = new XyzNamespace();
    retNs.setUuid(xyzNs.uuid());
    retNs.setAction(xyzNs.actionAsString());
    retNs.setAuthor(xyzNs.author());
    retNs.setPuuid(xyzNs.puuid());
    retNs.setTxn(toLong(xyzNs.txn().getValue()));
    retNs.setCreatedAt(toLong(xyzNs.createdAt()));
    retNs.setUpdatedAt(toLong(xyzNs.updatedAt()));
    retNs.setAppId(xyzNs.appId());
    retNs.setAuthorTime(toLong(xyzNs.authorTs()));
    retNs.setRealTimeUpdatedAt(toLong(xyzNs.updatedAt()));
    retNs.setVersion(xyzNs.version());
    retNs.setTags(getXyzTagsFromJbon(), false);
    retNs.setGrid(xyzNs.grid());

    return retNs;
  }

  private XyzTags getXyzTagsFromJbon() {
    if (tagsBytes == null) {
      return null;
    }
    // FIXME use existing DictManager.
    com.here.naksha.lib.jbon.XyzTags xyzTags =
        new com.here.naksha.lib.jbon.XyzTags(new JbDictManager()).mapBytes(tagsBytes, 0, tagsBytes.length);
    XyzTags tags = new XyzTags();
    tags.addAll(List.of(xyzTags.tagsArray()));
    return tags;
  }
}
