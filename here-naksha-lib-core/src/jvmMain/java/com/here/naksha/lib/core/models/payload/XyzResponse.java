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
package com.here.naksha.lib.core.models.payload;

import naksha.base.PlatformType;
import naksha.geo.GeoCollection;
import naksha.model.NakshaContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

/*
 * TODO CASL-798: review subclasses of XyzResponse from V2
 * Subclasses to review:
 *     {@link naksha.model.CountResponse}
 *     {@link naksha.model.ErrorResponse}
 *     {@link naksha.model.HealthStatus}
 *     {@link naksha.model.ModifiedEventResponse}
 *     {@link naksha.model.ModifiedResponseResponse}
 *     {@link naksha.model.StatisticsResponse}
 *     {@link naksha.model.StorageStatistics}
 *     {@link naksha.model.HistoryStatisticsResponse}
 *     {@link naksha.model.SuccessResponse}
 *     {@link naksha.model.NotModifiedResponse}
 *     {@link naksha.model.XyzFeatureCollection}
 *     {@link naksha.model.Changeset}
 *     {@link naksha.model.CompactChangeset}
 *     {@link naksha.model.ChangesetCollection}
 *     {@link naksha.model.ConnectorStatus}
 *     {@link naksha.model.SpaceStatus}
 */

// TODO: We need to make the documentation public:
//       https://here-technologies.atlassian.net/wiki/spaces/DataHub/pages/718971502/Connectors+Protocol

/**
 * All classes that represent a valid response of any remote procedure to the XYZ Hub need to extend this class.
 */
public abstract class XyzResponse extends GeoCollection {
  public static final PlatformType<XyzResponse> TYPE = forClass(XyzResponse.class);

  public static final String STREAM_ID = "streamId";
  public static final String ETAG = "etag";

  @Override
  public void onCreation() {
    super.onCreation();
    setStreamId(NakshaContext.currentContext().getStreamId());
  }

  /**
   * The unique stream-identifier of this request used to search in log files across the XYZ platform what happened while processing the
   * request.
   *
   * @return the unique stream-identifier of this request
   */
  public @NotNull String getStreamId() {
    return getOrCreate(STREAM_ID, String_TYPE, (_, _) -> NakshaContext.currentContext().getStreamId());
  }

  /**
   * Set the unique stream-identifier of this request used to search in log files across the XYZ platform what happened while processing the
   * request.
   *
   * @param streamId the unique stream-identifier to be set.
   */
  public void setStreamId(@NotNull String streamId) {
    set(STREAM_ID, streamId);
  }

  /**
   * An optional set e-tag which should be some value that allows the storage to check if the content of the response has changed.
   *
   * @return the e-tag, when it was calculated.
   */
  @SuppressWarnings("unused")
  public String getEtag() {
    return (String) getRaw(ETAG);
  }

  /**
   * Set the e-tag (a hash above all features), when it was calculated.
   *
   * @param etag the e-tag, if null, the e-tag is removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setEtag(@Nullable String etag) {
    if (etag == null) {
      delete(ETAG);
    } else {
      set(ETAG, etag);
    }
  }
}
