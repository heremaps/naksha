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
package com.here.naksha.lib.core.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.here.naksha.lib.core.models.payload.Payload;
import com.here.naksha.lib.core.models.payload.events.clustering.Clustering;
import com.here.naksha.lib.core.models.payload.events.tweaks.Tweaks;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import naksha.model.NakshaFeatureProxy;

/**
 * A base interface to be implemented by all types that are serializable and have a property "type" that holds the type of the object.
 */
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Payload.class),
  @JsonSubTypes.Type(value = Clustering.class),
  @JsonSubTypes.Type(value = Tweaks.class),
  @JsonSubTypes.Type(value = NakshaFeatureProxy.class),
})
public interface Typed extends JsonSerializable {}
