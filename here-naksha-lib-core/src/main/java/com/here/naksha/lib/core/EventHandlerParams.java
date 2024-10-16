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
package com.here.naksha.lib.core;

import com.here.naksha.lib.core.util.IoHelp;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class parse event handler parameters. */
@SuppressWarnings({"SameParameterValue", "unchecked"})
public abstract class EventHandlerParams {

  protected static final Logger logger = LoggerFactory.getLogger(EventHandlerParams.class);

  protected EventHandlerParams() {}

  /**
   * Parses the given value from the connector parameters using the default value, if the given
   * value is of an invalid type or not given.
   *
   * @param properties the properties of the event handler.
   * @param name The name of the parameter.
   * @param expectedType The type that is expected.
   * @param <T> The value-type.
   * @return the value.
   * @throws NullPointerException If the parameter not given.
   * @throws IllegalArgumentException If the parameter given, but not of the expected type.
   */
  protected <T> @NotNull T parseValue(
      @NotNull Map<@NotNull String, @Nullable Object> properties,
      @NotNull String name,
      @NotNull Class<T> expectedType) {
    return parseValueWithNullableDefault(properties, name, expectedType, null);
  }

  /**
   * Parses the given value from the connector parameters, return {@code null}, if the given value
   * not given.
   *
   * @param connectorParams The connector params.
   * @param name The name of the parameter.
   * @param expectedType The type that is expected.
   * @param <T> The value-type.
   * @return the value; {@code null} if the value not given.
   * @throws IllegalArgumentException If the parameter given, but not of the expected type.
   */
  protected <T> @Nullable T parseOptionalValue(
      @NotNull Map<@NotNull String, @Nullable Object> connectorParams,
      @NotNull String name,
      @NotNull Class<T> expectedType) {
    try {
      return parseValueWithNullableDefault(connectorParams, name, expectedType, null);
    } catch (NullPointerException e) {
      return null;
    }
  }

  /**
   * Parses the given value from the connector parameters using the default value, if the given
   * value is of an invalid type or not given.
   *
   * @param connectorParams the connector params.
   * @param name the name of the parameter.
   * @param defaultValue the default value to be used.
   * @param <T> the type of the value.
   * @return the value.
   */
  protected <T> @NotNull T parseValueWithDefault(
      @NotNull Map<@NotNull String, @Nullable Object> connectorParams,
      @NotNull String name,
      @NotNull T defaultValue)
      throws NullPointerException {
    return parseValueWithNullableDefault(connectorParams, name, (Class<T>) defaultValue.getClass(), defaultValue);
  }

  private <T> @NotNull T parseValueWithNullableDefault(
      @NotNull Map<@NotNull String, @Nullable Object> connectorParams,
      @NotNull String parameter,
      @NotNull Class<T> type,
      @Nullable T defaultValue)
      throws NullPointerException {
    final Object value = connectorParams.get(parameter);
    try {
      final T result = IoHelp.parseNullableValue(value, type, defaultValue, false);
      if (result == null) {
        throw new NullPointerException("Parameter " + parameter + " is null");
      }
      return result;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(IoHelp.format(
          "Cannot set value %s=%s. Load default '%s'",
          parameter, IoHelp.asString(value), IoHelp.asString(defaultValue)));
    }
  }
}
