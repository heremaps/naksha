/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.xyz.models.geojson.implementation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.xyz.Extensible;
import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Properties extends Extensible<Properties> {

  @JsonProperty(XyzNamespace.XYZ_NAMESPACE)
  @JsonInclude(Include.NON_NULL)
  private XyzNamespace xyzNamespace;

  @SuppressWarnings("WeakerAccess")
  public XyzNamespace getXyzNamespace() {
    return this.xyzNamespace;
  }

  @SuppressWarnings("WeakerAccess")
  public void setXyzNamespace(XyzNamespace xyzNamespace) {
    this.xyzNamespace = xyzNamespace;
  }

  @SuppressWarnings("WeakerAccess")
  public Properties withXyzNamespace(XyzNamespace xyzNamespace) {
    this.xyzNamespace = xyzNamespace;
    return this;
  }

  @SuppressWarnings("unused")
  public Set<String> keySet() {
    LinkedHashSet<String> keySet = new LinkedHashSet<>(getAdditionalProperties().keySet());
    keySet.add(XyzNamespace.XYZ_NAMESPACE);
    return keySet;
  }
}
