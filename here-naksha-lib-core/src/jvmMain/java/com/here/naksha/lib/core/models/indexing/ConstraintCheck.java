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
package com.here.naksha.lib.core.models.indexing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import naksha.base.JvmBoxingUtil;

/** A condition to check. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(value = "Check")
public class ConstraintCheck extends Constraint {

  /** The condition to apply. */
  public enum Test {
    /** If the property is not null; ignores {@link #getValue()}. */
    NOT_NULL,

    /** If the value of the property is unique; ignores {@link #getValue()}. */
    UNIQUE,

    /** If the value of the property is greater than the {@link #getValue()}. */
    GT,

    /** If the value of the property is greater than or equal to the {@link #getValue()}. */
    GTE,

    /** If the value of the property is equal to the {@link #getValue()}. */
    EQ,

    /** If the value of the property is less than the {@link #getValue()}. */
    LT,

    /** If the value of the property is less than or equal to the {@link #getValue()}. */
    LTE,

    /** If the length of the property is more than or equal to the defined {@link #getValue()}. */
    MIN_LEN,

    /** If the length of the property is less than or equal to the defined {@link #getValue()}. */
    MAX_LEN,

    /** If the value matches the given regular expression, given in the {@link #getValue()}. */
    MATCHES
  }

  private static final String TEST = "test";
  private static final String PATH = "path";
  private static final String VALUE = "value";

  /** The check to perform. */
  public Test getTest() {
    return JvmBoxingUtil.box(get(TEST), Test.class);
  }

  public void setTest(Test test) {
    setRaw(TEST, test);
  }

  /** The JSON path to the property to check. */
  public String getPath() {
    return (String) getRaw(PATH);
  }

  public void setPath(String path) {
    setRaw(PATH, path);
  }

  /** The optional value for the check. */
  public Object getValue() {
    return getRaw(VALUE);
  }

  public void setValue(Object value) {
    setRaw(VALUE, value);
  }
}
