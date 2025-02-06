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
package com.here.naksha.app.service.models;

import java.util.List;
import naksha.base.AnyObject;
import naksha.base.JvmAnyObjectUtil;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import org.jetbrains.annotations.NotNull;

public class FeatureCollectionRequest extends XyzRequest {

  private static final String FEATURES_KEY = "features";
  private static final String NEXT_PAGE_TOKEN_KEY = "nextPageToken";

  @Override
  public void onCreation() {
    setFeatures(new NakshaFeatureList());
    super.onCreation();
  }

  public @NotNull NakshaFeatureList getFeatures() {
    return JvmAnyObjectUtil.getProperty(this, FEATURES_KEY, NakshaFeatureList.class);
  }

  public void setFeatures(@NotNull List<? extends NakshaFeature> features) {
    setRaw(FEATURES_KEY, NakshaFeatureList.fromList(features));
  }

  public @NotNull FeatureCollectionRequest withFeatures(final @NotNull List<? extends @NotNull NakshaFeature> features) {
    setFeatures(features);
    return this;
  }

  /**
   * Returns the Space nextPageToken which is used to iterate above data.
   *
   * @return the nextPageToken.
   */
  public String getNextPageToken() {
    return JvmAnyObjectUtil.getProperty(this, NEXT_PAGE_TOKEN_KEY, String.class);
  }

  /**
   * Sets the Space nextPageToken that can be used to continue an iterate.
   *
   * @param nextPageToken the nextPageToken, if null the nextPageToken property is removed.
   */
  public void setNextPageToken(String nextPageToken) {
    setRaw(NEXT_PAGE_TOKEN_KEY, nextPageToken);
  }

  public @NotNull FeatureCollectionRequest withNextPageToken(final String nextPageToken) {
    setNextPageToken(nextPageToken);
    return this;
  }
}
