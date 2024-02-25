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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;

import com.here.naksha.lib.core.SimpleTask;
import com.here.naksha.lib.extmanager.models.ExtensionMetaData;
import com.here.naksha.lib.extmanager.models.KVPair;
import com.here.naksha.lib.extmanager.utils.ClassLoaderHelper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtensionCache {
  private static final @NotNull Logger logger = LoggerFactory.getLogger(ExtensionCache.class);
  private static ConcurrentHashMap<String, KVPair<ExtensionMetaData, ClassLoader>> loaderCache =
      new ConcurrentHashMap<>();
  private AtomicBoolean mutex = new AtomicBoolean(false);
  private JarClient jarClient;

  public ExtensionCache(JarClient jarClient) {
    this.jarClient = jarClient;
  }

  /**
   * Read extensions from database, download respective jars from S3 and store Extension to ClassLoader mapping
   * If any mapping already exist for extension version, It will skip that.
   * Also it removes existing mapping from cache which is not available in config store anymore
   */
  public void buildExtensionCache(List<ExtensionMetaData> metaDataList, ExtConfig extConfig) {
    List<Future<KVPair<ExtensionMetaData, File>>> futures = metaDataList.stream()
        .filter(metaData -> !this.isLoaderMappingExist(metaData))
        .map(exMetaData -> {
          SimpleTask<KVPair<ExtensionMetaData, File>> task = new SimpleTask<>();
          return task.start(() ->
              downloadJar(jarClient, extConfig.getAwsBucket(), exMetaData, extConfig.getTempPath()));
        })
        .collect(Collectors.toList());

    futures.stream().forEach(future -> {
      KVPair<ExtensionMetaData, File> result = null;
      try {
        result = future.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
        logger.error("Failed while downloading extension jar", e);
        // TODO log exception for extension which is not downloaded from s3 bucket
      }
      if (result != null) {
        ClassLoader loader = null;
        try {
          loader = ClassLoaderHelper.getClassLoader(result.getValue(), extConfig.getDelegatedClassList());
          if (loader != null)
            loaderCache.put(
                result.getKey().getId(),
                new KVPair<ExtensionMetaData, ClassLoader>(result.getKey(), loader));
        } catch (Exception e) {
          e.printStackTrace();
          // TODO log exception for extension where classloader can not be intantiated.
        }
      }
    });

    // Removing existing extension which has been removed from the db
    List<String> extIds =
        metaDataList.stream().map(ExtensionMetaData::getId).collect(Collectors.toList());
    for (String key : this.loaderCache.keySet()) {
      if (!extIds.contains(key)) this.loaderCache.remove(key);
    }

    System.out.println("Loader cache size " + this.loaderCache.size());
  }

  private boolean isLoaderMappingExist(ExtensionMetaData metaData) {
    return loaderCache.containsKey(metaData.getId())
        && loaderCache.get(metaData.getId()).getKey().getVersion().equals(metaData.getVersion());
  }

  /**
   * Lamda function which will initiate the downloading for extension jar
   */
  private KVPair<ExtensionMetaData, File> downloadJar(
      JarClient s3Client, String bucketName, ExtensionMetaData exMetaData, String jarPath) {
    logger.info("Downloading jar %s with version %s ", exMetaData.getKey());
    File file = null;
    try {
      file = s3Client.getJar(bucketName, exMetaData.getKey());
    } catch (IOException e) {
      logger.error("Failed to download jar %s ", exMetaData.getKey());
      throw unchecked(e);
    }
    return new KVPair<ExtensionMetaData, File>(exMetaData, file);
  }

  protected ClassLoader getClassLoaderById(@NotNull String extensionId) {
    if (loaderCache.containsKey(extensionId))
      return loaderCache.get(extensionId).getValue();
    return null;
  }

  protected ClassLoader getClassLoaderByName(@NotNull String extensionName) {
    KVPair<ExtensionMetaData, ClassLoader> mapper = loaderCache.values().stream()
        .filter(loaderMapper -> loaderMapper.getKey().getExtensionName().contains(extensionName))
        .findFirst()
        .orElse(null);

    if (mapper != null) return getClassLoaderById(mapper.getKey().getId());
    else return null;
  }

  public int getCacheLength() {
    return loaderCache.size();
  }

  public void clear() {
    this.loaderCache.clear();
  }

  public boolean getLock() {
    return this.mutex.compareAndSet(false, true);
  }

  public void unlock() {
    this.mutex.set(false);
  }
}
