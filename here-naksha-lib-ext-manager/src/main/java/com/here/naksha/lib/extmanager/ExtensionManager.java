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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.extmanager.models.ExtensionMetaData;
import com.here.naksha.lib.extmanager.utils.AmazonS3Client;
import com.here.naksha.lib.extmanager.utils.ScheduledTask;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class ExtensionManager implements IExtensionManager, Closeable {
  private final @NotNull INaksha naksha;
  private final ExtConfig extConfig;
  private ExtensionCache extensionCache;
  private ScheduledExecutorService scheduler;
  private JarClient jarClient;

  public ExtensionManager(@NotNull INaksha naksha, @NotNull ExtConfig extConfig) {
    this.naksha = naksha;
    this.extConfig = extConfig;
    this.extensionCache = new ExtensionCache(getS3Client());
    this.buildExtensionMap();
    this.scheduleRefreshCache();
  }

  protected JarClient getS3Client() {
    if (this.jarClient == null)
      this.jarClient = new AmazonS3Client(
          extConfig.getAwsAccessKey(),
          extConfig.getAwsSecretKey(),
          extConfig.getAwsRegion(),
          extConfig.getTempPath());
    return this.jarClient;
  }

  private void buildExtensionMap() {
    List<ExtensionMetaData> extMetaData = getExtensions();
    extensionCache.buildExtensionCache(extMetaData, extConfig);
  }

  @Override
  public void resetExtensionMap() {
    this.extensionCache.clear();
    this.buildExtensionMap();
  }

  @Override
  public Optional<ClassLoader> getClassLoaderById(@NotNull String extensionId) {
    ClassLoader loader = this.extensionCache.getClassLoaderById(extensionId);
    return loader == null ? Optional.empty() : Optional.of(loader);
  }

  @Override
  public Optional<ClassLoader> getClassLoaderByName(@NotNull String extensionName) {
    ClassLoader loader = this.extensionCache.getClassLoaderByName(extensionName);
    return loader == null ? Optional.empty() : Optional.of(loader);
  }

  private void scheduleRefreshCache() {
    scheduler = Executors.newSingleThreadScheduledExecutor();
    scheduler.scheduleAtFixedRate(
        new ScheduledTask(this.extensionCache, () -> getExtensions(), this.extConfig),
        0,
        extConfig.getRefreshScheduleInSeconds(),
        TimeUnit.SECONDS);
  }
  /**
   * TODO This method should fetch extensions from admin db.
   * as of now it is fetching extensions from local file.
   * @return
   */
  private List<ExtensionMetaData> getExtensions() {
    Path file = new File("src/test/resources/data/extension.txt").toPath();
    List<ExtensionMetaData> list = new ArrayList<>();
    try {
      String data = Files.readAllLines(file).stream().collect(Collectors.joining());
      list = new ObjectMapper().readValue(data, new TypeReference<List<ExtensionMetaData>>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return list;
  }

  @Override
  public void close() throws IOException {
    this.scheduler.shutdown();
  }
}
