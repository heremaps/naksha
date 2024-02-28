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

import com.amazonaws.SdkClientException;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.SimpleTask;
import com.here.naksha.lib.core.models.features.Extension;
import com.here.naksha.lib.core.models.features.ExtensionConfig;
import com.here.naksha.lib.extmanager.helpers.AmazonS3Helper;
import com.here.naksha.lib.extmanager.helpers.ClassLoaderHelper;
import com.here.naksha.lib.extmanager.helpers.FileHelper;
import com.here.naksha.lib.extmanager.models.ExtensionMapper;
import com.here.naksha.lib.extmanager.models.KVPair;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class contains registered extensions in naksha. It update/maintain extensions cache over scheduled time.
 */
public class ExtensionCache {
  private static final @NotNull Logger logger = LoggerFactory.getLogger(ExtensionCache.class);
  private static final ConcurrentHashMap<String, ExtensionMapper> loaderCache = new ConcurrentHashMap<>();
  private final Map<String, JarClient> jarClientMap;
  private final @NotNull INaksha naksha;

  public ExtensionCache(@NotNull INaksha naksha) {
    this.naksha = naksha;
    jarClientMap = new HashMap<>();
  }
  /**
   * Read extensions from database, download respective jars from configured client and store Extension to ClassLoader mapping
   * If it already have any mapping exist for extension then it simply skip that.
   * Also it removes existing mapping from cache which is not available in config store anymore
   */
  protected void buildExtensionCache(ExtensionConfig extensionConfig) {
    List<Future<KVPair<Extension, File>>> futures = extensionConfig.getExtensions().stream()
        .filter(extension -> !this.isLoaderMappingExist(extension))
        .map(extension -> {
          SimpleTask<KVPair<Extension, File>> task = new SimpleTask<>();
          return task.start(() -> downloadJar(extension));
        })
        .collect(Collectors.toList());

    futures.stream().forEach(future -> {
      KVPair<Extension, File> result = null;
      try {
        result = future.get();
      } catch (InterruptedException | ExecutionException e) {
        logger.error("Failed while downloading extension jar", e);
      }
      publishIntoCache(result, extensionConfig);
    });

    // Removing existing extension which has been removed from the configuration
    List<String> extIds = extensionConfig.getExtensions().stream()
        .map(Extension::getExtensionId)
        .collect(Collectors.toList());
    for (String key : loaderCache.keySet()) {
      if (!extIds.contains(key)) loaderCache.remove(key);
    }
    System.out.println("Loader cache size " + loaderCache.size());
  }

  private void publishIntoCache(KVPair<Extension, File> result, ExtensionConfig extensionConfig) {
    if (result != null && result.getValue() != null) {
      ClassLoader loader;
      try {
        loader = ClassLoaderHelper.getClassLoader(
            result.getValue(), extensionConfig.getWhilelistDelegateClass());
      } catch (Exception e) {
        logger.error("Failed to load extension jar " + result.getKey().getExtensionId(), e);
        return;
      }

      Object object = null;
      if (result.getKey().getInitClassName() == null
          || result.getKey().getInitClassName().isEmpty()) {
        loaderCache.put(result.getKey().getExtensionId(), new ExtensionMapper(result.getKey(), loader, object));
      } else {
        try {
          Class<?> clz = loader.loadClass(result.getKey().getInitClassName());
          object = clz.getConstructor(INaksha.class, Object.class)
              .newInstance(naksha, result.getKey().getProperties());
          loaderCache.put(
              result.getKey().getExtensionId(), new ExtensionMapper(result.getKey(), loader, object));
        } catch (ClassNotFoundException
            | InvocationTargetException
            | InstantiationException
            | NoSuchMethodException
            | IllegalAccessException e) {
          logger.error(
              String.format(
                  "Failed to instantiate class %s for extension %s ",
                  result.getKey().getInitClassName(),
                  result.getKey().getExtensionId()),
              e);
        }
      }
    }
  }

  private boolean isLoaderMappingExist(Extension extension) {
    boolean isEqual = loaderCache.containsKey(extension.getExtensionId());
    if (isEqual) {
      Extension exExtension = loaderCache.get(extension.getExtensionId()).getExtension();
      isEqual = exExtension.getUrl().equals(extension.getUrl())
          && exExtension.getVersion().equals(extension.getVersion())
          && exExtension.getInitClassName().equals(extension.getInitClassName());
    }
    return isEqual;
  }

  /**
   * Lamda function which will initiate the downloading for extension jar
   */
  private KVPair<Extension, File> downloadJar(Extension extension) {
    logger.info("Downloading jar {} with version {} ", extension.getExtensionId(), extension.getVersion());
    JarClient client = getJarClient(extension.getUrl());
    File file = null;
    try {
      file = client.getJar(extension.getUrl());
    } catch (IOException | SdkClientException e) {
      logger.error("Failed to fetch jar {} ", extension.getUrl());
    }
    return new KVPair<Extension, File>(extension, file);
  }

  // TODO: Can be moved to factory function. Since not used elsewhere placed it inside this class
  protected JarClient getJarClient(String url) {
    JarClient jarClient;
    if (url.startsWith(JarClientType.S3.getType())) {
      jarClient = jarClientMap.get(JarClientType.S3.getType());
      if (jarClient == null) {
        jarClient = new AmazonS3Helper();
        jarClientMap.put(JarClientType.S3.getType(), jarClient);
      }
      return jarClient;
    } else if (url.startsWith(JarClientType.FILE.getType())) {
      jarClient = jarClientMap.get(JarClientType.FILE.getType());
      if (jarClient == null) {
        jarClient = new FileHelper();
        jarClientMap.put(JarClientType.FILE.getType(), jarClient);
      }
      return jarClient;
    } else throw new UnsupportedOperationException("Jar client not configured for url " + url);
  }

  protected ClassLoader getClassLoaderById(@NotNull String extensionId) {
    if (loaderCache.containsKey(extensionId))
      return loaderCache.get(extensionId).getClassLoader();
    return null;
  }

  public int getCacheLength() {
    return loaderCache.size();
  }

  public List<Extension> getCachedExtensions() {
    return loaderCache.values().stream().map(ExtensionMapper::getExtension).collect(Collectors.toList());
  }

  public void clear() {
    loaderCache.clear();
  }

  public enum JarClientType {
    S3("s3:"),
    FILE("file:");

    private final String type;

    JarClientType(String type) {
      this.type = type;
    }

    public String getType() {
      return this.type;
    }
  }
}
