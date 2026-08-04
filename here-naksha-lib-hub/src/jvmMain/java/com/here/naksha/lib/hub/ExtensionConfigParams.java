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
package com.here.naksha.lib.hub;

import static naksha.base.JvmAnyObjectUtil.getOrSetProperty;
import static naksha.base.JvmAnyObjectUtil.getProperty;

import java.util.List;
import naksha.base.PAnyMap;
import naksha.base.StringList;

public class ExtensionConfigParams extends PAnyMap {

  public static final String WHITELIST_CLASSES = "whitelistClasses";
  public static final String INTERVAL_MS = "intervalms";
  public static final String EXTENSION_ROOT_PATH = "extensionsRootPath";

  private static final StringList DEFAULT_WHITELIST_CLASSES = StringList.of("java.*", "javax.*", "com.here.naksha.*");
  private static final Integer DEFAULT_INTERVAL_MS = 300_000;

  /**
   * @return List of whitelist urls used in classloader
   */
  public List<String> getWhiteListClasses() {
    return getOrSetProperty(this, WHITELIST_CLASSES, DEFAULT_WHITELIST_CLASSES);
  }

  /**
   * @return config expiry in millisecond
   */
  public long getIntervalMs() {
    return getOrSetProperty(this, INTERVAL_MS, DEFAULT_INTERVAL_MS);
  }

  /**
   * @return extensions root directory
   */
  public String getExtensionRootPath() {
    return getProperty(this, EXTENSION_ROOT_PATH, String.class);
  }
}
