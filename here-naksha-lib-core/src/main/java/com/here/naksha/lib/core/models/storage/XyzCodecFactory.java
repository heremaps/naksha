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
package com.here.naksha.lib.core.models.storage;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import naksha.model.XyzFeature;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The default codec factory for {@link XyzFeature}'s.
 */
@SuppressWarnings({"rawtypes", "ReassignedVariable", "unchecked", "deprecation", "SuspiciousMethodCalls"})
public abstract class XyzCodecFactory<FEATURE extends XyzFeature, CODEC extends XyzCodec<FEATURE, CODEC>>
    implements FeatureCodecFactory<FEATURE, CODEC> {

  XyzCodecFactory() {
    codecClass = (Class<CODEC>) newInstance().getClass();
  }

  private final @NotNull Class<CODEC> codecClass;

  private static final ConcurrentHashMap<Class<? extends XyzCodecFactory>, XyzCodecFactory> cache =
      new ConcurrentHashMap<>();

  /**
   * Returns the factory singleton.
   *
   * @return the factory singleton.
   */
  public static <
          FEATURE extends XyzFeature,
          CODEC extends XyzCodec<FEATURE, CODEC>,
          FACTORY extends XyzCodecFactory<FEATURE, CODEC>>
      @NotNull FACTORY getFactory(@NotNull Class<FACTORY> factoryClass) {
    XyzCodecFactory factory = cache.get(factoryClass);
    if (factory == null) {
      try {
        factory = factoryClass.newInstance();
        final XyzCodecFactory existing = cache.putIfAbsent(factoryClass, factory);
        if (existing != null) {
          return (FACTORY) existing;
        }
      } catch (Exception e) {
        throw unchecked(e);
      }
    }
    return (FACTORY) factory;
  }

  @Override
  public abstract @NotNull CODEC newInstance();

  @Override
  public boolean isInstance(@Nullable FeatureCodec<?, ?> codec) {
    return codecClass.isInstance(codec);
  }
}
