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
package com.here.naksha.app.service.http.auth;

import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.auth.AttributeMap;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.Space;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class XyzHubAttributeMap extends AttributeMap {

  public static final String ID = "id";
  public static final String AUTHOR = "author";
  public static final String SPACE = "space";
  public static final String CONNECTOR = "connector";
  public static final String PACKAGES = "packages";

  @Deprecated
  public static final String STORAGE = "storage";

  @Deprecated
  public static final String LISTENERS = "listeners";

  @Deprecated
  public static final String PROCESSORS = "processors";

  @Deprecated
  public static final String SEARCHABLE_PROPERTIES = "searchableProperties";

  @Deprecated
  public static final String SORTABLE_PROPERTIES = "sortableProperties";

  /**
   * Returns the attribute map that is required for all entities, for example all spaces, connectors aso.
   *
   * @return The attribute map that is required for all entities, for example all spaces, connectors aso.
   */
  public static @NotNull AttributeMap ofAll() {
    // Note: An empty map means “all”!
    return new AttributeMap();
  }

  /**
   * Returns the attribute map of the given feature.
   *
   * @param feature The feature for which to return the attribute map.
   * @return The attribute map of the given feature.
   */
  public static @NotNull AttributeMap ofFeature(@NotNull XyzFeature feature) {
    final AttributeMap attributeMap = new AttributeMap();
    attributeMap.withValue(XyzHubAttributeMap.ID, feature.getId());
    attributeMap.withValue(
        XyzHubAttributeMap.AUTHOR,
        feature.getProperties().getXyzNamespace().getAuthor());
    return attributeMap;
  }

  /**
   * Returns the attribute map of the given space.
   *
   * @param space The space for which to return the attribute map.
   * @return The attribute map of the given space.
   */
  public static @NotNull AttributeMap ofSpace(@NotNull Space space) {
    final AttributeMap attributeMap = new AttributeMap();
    attributeMap.withValue(XyzHubAttributeMap.SPACE, space.getId());
    attributeMap.withValue(
        XyzHubAttributeMap.AUTHOR,
        space.getProperties().getXyzNamespace().getAuthor());
    if (space.getPackages() != null) {
      attributeMap.withValue(XyzHubAttributeMap.PACKAGES, space.getPackages()); // oneOf
    }
    return attributeMap;
  }

  /**
   * Returns the attribute map of the given package.
   *
   * @param packageId The package for which to return the attribute map.
   * @return The attribute map of the given package.
   */
  public static @NotNull AttributeMap ofPackage(@NotNull String packageId) {
    final AttributeMap attributeMap = new AttributeMap();
    attributeMap.withValue(XyzHubAttributeMap.PACKAGES, packageId);
    return attributeMap;
  }

  /**
   * Returns the attribute map of the given connector.
   *
   * @param eventHandler The connector for which to return the attribute map.
   * @return The attribute map of the given connector.
   */
  public static @NotNull AttributeMap ofConnector(@NotNull EventHandler eventHandler) {
    final AttributeMap attributeMap = new AttributeMap();
    attributeMap.withValue(XyzHubAttributeMap.CONNECTOR, eventHandler.getId());
    attributeMap.withValue(
        XyzHubAttributeMap.AUTHOR,
        eventHandler.getProperties().getXyzNamespace().getAuthor());
    if (eventHandler.getPackages() != null) {
      attributeMap.withValue(XyzHubAttributeMap.PACKAGES, eventHandler.getPackages()); // oneOf
    }
    return attributeMap;
  }

  /**
   * Returns the attribute map of the given connector.
   *
   * @param connectorId The ID of the connector for which to return the attribute map.
   * @return The attribute map of the given connector.
   * @throws XyzErrorException If no such connector exists.
   */
  public static @NotNull AttributeMap ofConnectorById(@NotNull String connectorId) {
    //    final Connector connector = INaksha.instance.get().getConnectorById(connectorId);
    //    if (connector == null) {
    //      throw new XyzErrorException(XyzError.ILLEGAL_ARGUMENT, "Unknown connector " + connectorId);
    //    }
    //    return ofConnector(connector);
    return ofConnector(null);
  }

  @Deprecated
  public static XyzHubAttributeMap forValues(String author, String space, List<String> packages) {
    XyzHubAttributeMap attributeMap = new XyzHubAttributeMap();
    attributeMap.withValue(AUTHOR, author);
    attributeMap.withValue(SPACE, space);
    attributeMap.withValue(PACKAGES, packages);
    return attributeMap;
  }

  @Deprecated
  public static XyzHubAttributeMap forIdValues(String id) {
    XyzHubAttributeMap attributeMap = new XyzHubAttributeMap();
    attributeMap.withValue(ID, id);
    return attributeMap;
  }

  @Deprecated
  public static XyzHubAttributeMap forIdValues(String author, String id) {
    XyzHubAttributeMap attributeMap = new XyzHubAttributeMap();
    attributeMap.withValue(AUTHOR, author);
    attributeMap.withValue(ID, id);
    return attributeMap;
  }
}
