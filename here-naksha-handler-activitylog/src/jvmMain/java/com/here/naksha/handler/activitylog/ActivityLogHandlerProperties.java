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
package com.here.naksha.handler.activitylog;

import naksha.base.PlatformType;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;

import static naksha.base.Platform.forClass;

@AvailableSince(NakshaVersion.v2_0_14)
public class ActivityLogHandlerProperties extends NakshaProperties {

  public static final PlatformType<ActivityLogHandlerProperties> TYPE = forClass(ActivityLogHandlerProperties.class);

  @AvailableSince(NakshaVersion.v2_0_14)
  public static final String SPACE_ID = "spaceId";

  public String getSpaceId() {
    return (String) getRaw(SPACE_ID);
  }

  public static ActivityLogHandlerProperties activityLogHandlerProperties(String spaceId) {
    ActivityLogHandlerProperties properties = new ActivityLogHandlerProperties();
    properties.setSpaceId(spaceId);
    return properties;
  }

  private void setSpaceId(String spaceId) {
    setRaw(SPACE_ID, spaceId);
  }
}
