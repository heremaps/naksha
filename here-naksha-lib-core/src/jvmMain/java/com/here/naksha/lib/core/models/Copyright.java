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

import naksha.base.ListProxy;
import naksha.base.PlatformType;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.forClass;

/**
 * The copyright information object.
 */
public class Copyright extends NakshaFeature {
  public static final PlatformType<Copyright> TYPE = forClass(Copyright.class);

  private static final String LABEL = "label";
  private static final String ALT = "alt";

  /**
   * The copyright label to be displayed by the client.
   */
  public @Nullable String getLabel() {
    return getAs(LABEL, String_TYPE);
  }

  public void setLabel(final @Nullable String label) {
    set(LABEL, label);
  }

  public @NotNull Copyright withLabel(final @Nullable String label) {
    setLabel(label);
    return this;
  }

  /**
   * The description text for the label to be displayed by the client.
   */
  public String getAlt() {
    return getAs(ALT, String_TYPE);
  }

  public void setAlt(final String alt) {
    set(ALT, alt);
  }

  public @NotNull Copyright withAlt(final String alt) {
    setAlt(alt);
    return this;
  }

  public static class List extends ListProxy<Copyright> {
    public List() {
      super(Copyright.TYPE);
    }
  }
}
