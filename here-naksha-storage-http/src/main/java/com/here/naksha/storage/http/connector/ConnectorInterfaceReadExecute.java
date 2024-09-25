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
package com.here.naksha.storage.http.connector;

import static com.here.naksha.common.http.apis.ApiParamsConst.FEATURE_IDS;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByIdEvent;
import com.here.naksha.lib.core.models.storage.ReadFeaturesProxyWrapper;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.PrepareResult;
import com.here.naksha.storage.http.RequestSender;
import java.net.http.HttpResponse;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ConnectorInterfaceReadExecute {

  @NotNull
  public static Result execute(ReadFeaturesProxyWrapper request, RequestSender sender) {
    return switch (request.getReadRequestType()) {
      case GET_BY_IDS -> executeFeaturesByIds(request, sender);
      default -> throw new IllegalStateException("Unexpected value: " + request.getReadRequestType());
    };
  }

  private static Result executeFeaturesByIds(ReadFeaturesProxyWrapper request, RequestSender sender) {
    Event event = createFeaturesByIdsEvent(request);

    String jsonEvent = JsonSerializable.serialize(event);
    HttpResponse<byte[]> httpResponse = post(sender, jsonEvent);

    return PrepareResult.prepareResult(httpResponse, XyzFeatureCollection.class, XyzFeatureCollection::getFeatures);
  }

  private static Event createFeaturesByIdsEvent(ReadFeaturesProxyWrapper request) {
    String dataHubSpaceName = request.getCollections().get(0);
    Space dataHubSpace = new Space(dataHubSpaceName);
    List<String> id = request.getQueryParameter(FEATURE_IDS);
    Event getFeaturesByIdEvent = new GetFeaturesByIdEvent().withIds(id);

    getFeaturesByIdEvent.setSpace(dataHubSpace);
    return getFeaturesByIdEvent;
  }

  private static HttpResponse<byte[]> post(RequestSender sender, String body) {
    return sender.sendRequest("", true, null, "POST", body);
  }
}
