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
package com.here.naksha.mom10;

class DeltaProperties {
  private DeltaProperties() {}

  /*
  Properties that are part of both pre-MOM 10 delta NS and MOM 10+ moderationInfo
  pre MOM 10: https://docs.in.here.com/static/169823/1467398/html/#com/here/mom/internal/extension/branch/branch.html
  MOM 10+: https://here-dev.zoominsoftware.io/docs/bundle/map-object-model-data-specification-10/page/com/here/mom/internal/component/moderation/moderation-information.html
  */
  public static final String ORIGIN_ID = "originId";
  public static final String PARENT_LINK = "parentLink";
  public static final String CHANGE_STATE = "changeState";
  public static final String REVIEW_STATE = "reviewState";
}
