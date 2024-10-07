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
package naksha.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.models.payload.Event;
import org.jetbrains.annotations.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "ModifiedEventResponse")
public class ModifiedEventResponse extends ModifiedPayloadResponse {

  private Event event;

  public ModifiedEventResponse(@NotNull String type) {
    super(type);
  }

  public Event getEvent() {
    return this.event;
  }

  public void setEvent(Event event) {
    this.event = event;
  }

  public ModifiedEventResponse withEvent(Event event) {
    setEvent(event);
    return this;
  }

  @Override
  public int resultSize() {
    return 0;
  }
}