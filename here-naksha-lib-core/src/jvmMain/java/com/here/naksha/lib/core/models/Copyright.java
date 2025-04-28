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

import naksha.base.JvmListProxy;
import naksha.model.objects.NakshaFeature;

/**
 * The copyright information object.
 */
public class Copyright extends NakshaFeature {

  private static final String LABEL = "label";
  private static final String ALT = "alt";

  /**
   * The copyright label to be displayed by the client.
   */
  public String getLabel() {
    return (String) getRaw(LABEL);
  }

  public void setLabel(final String label) {
    setRaw(LABEL, label);
  }

  public Copyright withLabel(final String label) {
    setLabel(label);
    return this;
  }

  /**
   * The description text for the label to be displayed by the client.
   */
  public String getAlt() {
    return (String) getRaw(ALT);
  }

  public void setAlt(final String alt) {
    setRaw(ALT, alt);
  }

  public Copyright withAlt(final String alt) {
    setAlt(alt);
    return this;
  }

  public static class List extends JvmListProxy<Copyright> {
    public List() {
      super(Copyright.class);
    }
  }
}
