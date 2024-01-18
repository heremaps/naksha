/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.app.service.http.tasks;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum DeletionStrategy {
  /**
   * When using {{@link DeletionStrategy#SOFT_DELETE}} Resource deletion request will end up with resource being "softly deleted" - it will
   * still be persisted and recoverable but until recovered it won't be fetch-able via read operations.
   */
  SOFT_DELETE("soft"),

  /**
   * When using {{@link DeletionStrategy#HARD_DELETE}} Resource deletion request will end up with resource being utterly purged from the
   * system without possibility to recover.
   */
  HARD_DELETE("hard");

  private static final Map<String, DeletionStrategy> strategyBySlug =
      stream(values()).collect(toMap(DeletionStrategy::getSlug, Function.identity()));

  private final String slug;

  DeletionStrategy(String slug) {
    this.slug = slug;
  }

  public String getSlug() {
    return slug;
  }

  static DeletionStrategy bySlugOrElseDefault(@Nullable String slug, @NotNull DeletionStrategy defaultStrategy) {
    return strategyBySlug.getOrDefault(slug, defaultStrategy);
  }
}
