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
package com.here.naksha.lib.core.models.geojson.implementation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereDeltaNs;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereMetaNs;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzActivityLog;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.util.json.JsonObject;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The standard properties of the standard feature store in the Naksha-Hub. */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public class XyzProperties extends JsonObject {

  /**
   * The internal management properties of the Naksha-Hub, XYZ-Hub and other XYZ derivatives.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String XYZ_NAMESPACE = "@ns:com:here:xyz";

  /**
   * Properties used by the deprecated Activity-Log service, just here to allow downward
   * compatibility.
   */
  @AvailableSince(NakshaVersion.v2_0_0)
  public static final String XYZ_ACTIVITY_LOG_NS = "@ns:com:here:xyz:log";

  /**
   * Properties used for the base layer (used in consistent store).
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String HERE_META_NS = "@ns:com:here:mom:meta";

  /**
   * Properties used for the delta layer (only within the moderation sub-process).
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String HERE_DELTA_NS = "@ns:com:here:mom:delta";

  public XyzProperties() {}

  @JsonProperty(XYZ_NAMESPACE)
  @JsonInclude(Include.NON_NULL)
  private XyzNamespace xyzNamespace;

  @AvailableSince(NakshaVersion.v2_0_11)
  public static final String XYZ_REFERENCES = "references";

  @JsonProperty(XYZ_REFERENCES)
  @JsonInclude(Include.NON_NULL)
  private @Nullable List<XyzReference> references;

  /**
   * Returns the XYZ namespace, guaranteed to be always present. If parsing a feature without such
   * property, an empty one will be created.
   *
   * @return The namespace.
   */
  @JsonIgnore
  public @NotNull XyzNamespace getXyzNamespace() {
    if (xyzNamespace == null) {
      return xyzNamespace = new XyzNamespace();
    }
    return xyzNamespace;
  }

  /**
   * Sets the XYZ namespace, must never be {@code null}!
   *
   * @param xyzNamespace The namespace, must not be {@code null}!
   */
  @JsonIgnore
  public void setXyzNamespace(@NotNull XyzNamespace xyzNamespace) {
    this.xyzNamespace = xyzNamespace;
  }

  /**
   * Returns the XYZ namespace, guaranteed to be always present. If parsing a feature without such
   * property, an empty one will be created.
   *
   * @return The namespace.
   */
  @JsonIgnore
  public @NotNull XyzNamespace useXyzNamespace() {
    if (xyzNamespace == null) {
      return xyzNamespace = new XyzNamespace();
    }
    return xyzNamespace;
  }

  @JsonProperty(XYZ_ACTIVITY_LOG_NS)
  @JsonInclude(Include.NON_NULL)
  private @Nullable XyzActivityLog xyzActivityLog;

  /**
   * Returns the activity log namespace.
   *
   * @return The activity log namespace; if any.
   */
  @JsonIgnore
  public @Nullable XyzActivityLog getXyzActivityLog() {
    return xyzActivityLog;
  }

  /**
   * Sets the activity log namespace.
   *
   * @param ns The namespace.
   */
  @JsonIgnore
  public void setXyzActivityLog(@Nullable XyzActivityLog ns) {
    this.xyzActivityLog = ns;
  }

  /**
   * Removes the activity log namespace.
   *
   * @return The activity log namespace; if there was any.
   * @deprecated Can be replaced with {@code setXyzActivityLog(null);}.
   */
  @JsonIgnore
  @Deprecated
  public @Nullable XyzActivityLog removeActivityLog() {
    final XyzActivityLog xyzActivityLog = this.xyzActivityLog;
    this.xyzActivityLog = null;
    return xyzActivityLog;
  }

  @JsonProperty(HERE_META_NS)
  @JsonInclude(Include.NON_NULL)
  private @Nullable HereMetaNs __meta;

  @JsonIgnore
  public @NotNull HereMetaNs useMetaNamespace() {
    HereMetaNs meta = __meta;
    if (meta == null) {
      this.__meta = meta = new HereMetaNs();
      // TODO: Initialize it correctly, e.g. change counter, streamId, change-state and more!
    }
    return meta;
  }

  @JsonIgnore
  public @Nullable HereMetaNs getMetaNamespace() {
    return __meta;
  }

  @JsonIgnore
  public @Nullable HereMetaNs setMetaNamespace(@Nullable HereMetaNs ns) {
    final HereMetaNs old = this.__meta;
    __meta = ns;
    return old;
  }

  @JsonIgnore
  public @NotNull XyzProperties withMetaNamespace(@Nullable HereMetaNs ns) {
    setMetaNamespace(ns);
    return this;
  }

  @JsonProperty(HERE_DELTA_NS)
  @JsonInclude(Include.NON_NULL)
  private @Nullable HereDeltaNs __delta;

  @JsonIgnore
  public @NotNull HereDeltaNs useDeltaNamespace() {
    HereDeltaNs delta = __delta;
    if (delta == null) {
      this.__delta = delta = new HereDeltaNs();
    }
    return delta;
  }

  @JsonIgnore
  public @Nullable HereDeltaNs getDeltaNamespace() {
    return __delta;
  }

  @JsonIgnore
  public @Nullable HereDeltaNs setDeltaNamespace(@Nullable HereDeltaNs ns) {
    final HereDeltaNs old = this.__delta;
    __delta = ns;
    return old;
  }

  @JsonIgnore
  public @NotNull XyzProperties withDeltaNamespace(@Nullable HereDeltaNs ns) {
    setDeltaNamespace(ns);
    return this;
  }

  public @Nullable List<XyzReference> getReferences() {
    return references;
  }

  public void setReferences(@Nullable List<XyzReference> references) {
    this.references = references;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    XyzProperties that = (XyzProperties) o;
    return Objects.equals(xyzNamespace, that.xyzNamespace)
        && Objects.equals(references, that.references)
        && Objects.equals(xyzActivityLog, that.xyzActivityLog);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), xyzNamespace, references, xyzActivityLog);
  }
}
