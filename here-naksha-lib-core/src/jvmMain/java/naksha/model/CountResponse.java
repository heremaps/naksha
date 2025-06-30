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
import naksha.base.NotNullBoolProperty;
import naksha.base.NotNullProperty;
import naksha.base.NullableBoolProperty;
import naksha.base.PlatformType;
import naksha.model.request.Response;
import org.jetbrains.annotations.NotNull;

import static naksha.base.NakshaBaseKt.Long_TYPE;
import static naksha.base.Platform.forClass;

/** The response providing the count of features in a space. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "CountResponse")
@Deprecated
public class CountResponse extends Response {
  public static final PlatformType<CountResponse> TYPE = forClass(CountResponse.class).withJsonType("CountResponse");
  private static final NotNullProperty<CountResponse, Long> COUNT$ = new NotNullProperty<>(Long_TYPE, "count", (self, name) -> 0L);
  private static final NotNullBoolProperty<CountResponse> ESTIMATED$ = new NotNullBoolProperty<>("estimated");

  /**
   * Returns the proprietary count property that is used by Space count requests to return the
   * number of features found.
   *
   * @return the amount of features that are matching the query.
   */
  @SuppressWarnings("unused")
  public @NotNull Long getCount() {
    return COUNT$.getValue(this);
  }

  /**
   * Sets the amount of features that where matching a query, without returning the features (so
   * features will be null or an empty array).
   *
   * @param count the amount of features that where matching a query, if null, then the property is
   *     removed.
   */
  @SuppressWarnings("WeakerAccess")
  public void setCount(@NotNull Long count) {
    COUNT$.setValue(this, count);
  }

  /**
   * Returns the estimated flag that defines, if the value of the count property is an estimation.
   *
   * @return true, if the value of the count property is an estimation.
   */
  @SuppressWarnings("unused")
  public boolean getEstimated() {
    return ESTIMATED$.getValue(this);
  }

  /**
   * Sets the estimated flag that defines, if the count property is an estimation.
   *
   * @param estimated the estimated flag that defines, if the count property is an estimation.
   */
  @SuppressWarnings("WeakerAccess")
  public void setEstimated(boolean estimated) {
    ESTIMATED$.setValue(this, estimated);
  }
}
