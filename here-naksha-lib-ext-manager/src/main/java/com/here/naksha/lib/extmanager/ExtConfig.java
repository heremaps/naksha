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
package com.here.naksha.lib.extmanager;

import java.util.Arrays;
import java.util.List;

public final class ExtConfig {
  String awsAccessKey = "";
  String awsSecretKey = "";
  String awsRegion = "eu-west-1";
  String awsBucket = "";
  String tempPath = System.getProperty("java.io.tmpdir");
  String whitelistedDelegatedClases = "java.*,javax.*,com.here.samples.*,org.locationtech.jts.*";
  long refreshSchedule = 10;

  public String getAwsAccessKey() {
    return awsAccessKey;
  }

  public String getAwsSecretKey() {
    return awsSecretKey;
  }

  public String getAwsRegion() {
    return awsRegion;
  }

  public String getAwsBucket() {
    return awsBucket;
  }

  public String getTempPath() {
    return tempPath;
  }

  public List<String> getDelegatedClassList() {
    return Arrays.asList(whitelistedDelegatedClases.split(","));
  }

  public long getRefreshScheduleInSeconds() {
    return refreshSchedule;
  }
}
