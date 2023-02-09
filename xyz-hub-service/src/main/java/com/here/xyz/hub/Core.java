/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

package com.here.xyz.hub;

import static com.here.xyz.util.JsonConfigFile.nullable;

import com.here.xyz.config.XyzConfig;
import com.here.xyz.hub.util.metrics.net.ConnectionMetrics.HubMetricsFactory;
import com.here.xyz.util.JsonConfigFile;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.util.CachedClock;
import org.apache.logging.log4j.core.util.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Core {

  private static final Logger logger = LogManager.getLogger();

  protected Core(@Nullable VertxOptions vertxOptions) throws IOException {
    if (vertxOptions == null) {
      vertxOptions = new VertxOptions();
    }
    vertx = Vertx.vertx(vertxOptions);
    router = Router.router(vertx);

    config = XyzConfig.get();
    // TODO: Add environment and region!
    config.USER_AGENT += '/' + BUILD_VERSION;

    if (config.START_METRICS) {
      vertxOptions.setMetricsOptions(new MetricsOptions().setEnabled(true).setFactory(new HubMetricsFactory()));
    }

    Configurator.reconfigure(NetUtils.toURI(config.LOG_CONFIG()));
    if (config.DEBUG) {
      Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.getLevel("DEBUG"));
    } else {
      Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.INFO);
    }
    vertxOptions.setWorkerPoolSize(config.vertxWorkerPoolSize());
    vertxOptions.setPreferNativeTransport(true);
    if (config.DEBUG) {
      vertxOptions
          .setBlockedThreadCheckInterval(TimeUnit.MINUTES.toMillis(1))
          .setMaxEventLoopExecuteTime(TimeUnit.MINUTES.toMillis(1))
          .setMaxWorkerExecuteTime(TimeUnit.MINUTES.toMillis(1))
          .setWarningExceptionTime(TimeUnit.MINUTES.toMillis(1));
    }
    webClient = WebClient.create(vertx,
        new WebClientOptions()
            .setUserAgent(config.USER_AGENT)
            .setTcpKeepAlive(config.HTTP_CLIENT_TCP_KEEPALIVE)
            .setIdleTimeout(config.HTTP_CLIENT_IDLE_TIMEOUT)
            .setTcpQuickAck(true)
            .setTcpFastOpen(true)
            .setPipelining(config.HTTP_CLIENT_PIPELINING));
  }

  /**
   * The entry point to the Vert.x core API.
   */
  public final @NotNull Vertx vertx;

  /**
   * The configuration of the core service, the same as returned via {@link XyzConfig#get()}.
   */
  public final @NotNull XyzConfig config;

  /**
   * A web client to access XYZ Hub nodes and other web resources.
   */
  public final WebClient webClient;

  /**
   * The router of the service.
   */
  public final @NotNull Router router;

  /**
   * A cached clock instance.
   */
  private static final CachedClock clock = CachedClock.instance();

  public static long currentTimeMillis() {
    return clock.currentTimeMillis();
  }

  /**
   * The service start time.
   */
  public static final long START_TIME = currentTimeMillis();

  /**
   * The Vertx worker pool size environment variable.
   */
  protected static final String VERTX_WORKER_POOL_SIZE = "VERTX_WORKER_POOL_SIZE";

  /**
   * The build time.
   */
  public static final long BUILD_TIME = getBuildTime();

  /**
   * The build version.
   */
  public static final String BUILD_VERSION = getBuildProperty("xyzhub.version");

  /**
   * Read a file either from "~/.xyz-hub" or from the resources. The location of home can be overridden using the environment variable
   * XYZ_CONFIG_PATH.
   *
   * @param filename the filename of the file to read, e.g. "auth/jwt.key".
   * @return the bytes of the file.
   * @throws IOException if the file does not exist or any other error occurred.
   */
  public static byte @NotNull [] readFileFromHomeOrResource(@NotNull String filename) throws IOException {
    //noinspection ConstantConditions
    if (filename == null) {
      throw new FileNotFoundException("null");
    }
    final char first = filename.charAt(0);
    if (first == '/' || first == '\\') {
      filename = filename.substring(1);
    }

    final String pathEnvName = JsonConfigFile.configPathEnvName(XyzConfig.class);
    final String path = nullable(System.getenv(pathEnvName));
    final Path filePath;
    if (path != null) {
      filePath = Paths.get(path, filename).toAbsolutePath();
    } else {
      final String userHome = System.getProperty("user.home");
      if (userHome != null) {
        filePath = Paths.get(userHome, ".xyz-hub", filename).toAbsolutePath();
      } else {
        filePath = null;
      }
    }
    if (filePath != null) {
      final File file = filePath.toFile();
      if (file.exists() && file.isFile() && file.canRead()) {
        return Files.readAllBytes(filePath);
      }
    }

    try (final InputStream is = Core.class.getClassLoader().getResourceAsStream(filename)) {
      if (is == null) {
        throw new FileNotFoundException(filename);
      }
      return readNBytes(is, Integer.MAX_VALUE);
    }
  }

  private static final int DEFAULT_BUFFER_SIZE = 8192;
  private static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

  // Taken from JDK 9+
  private static byte[] readNBytes(final InputStream is, int len) throws IOException {
    if (len < 0) {
      throw new IllegalArgumentException("len < 0");
    }

    List<byte[]> bufs = null;
    byte[] result = null;
    int total = 0;
    int remaining = len;
    int n;
    do {
      byte[] buf = new byte[Math.min(remaining, DEFAULT_BUFFER_SIZE)];
      int nread = 0;

      // read to EOF which may read more or less than buffer size
      while ((n = is.read(buf, nread,
          Math.min(buf.length - nread, remaining))) > 0) {
        nread += n;
        remaining -= n;
      }

      if (nread > 0) {
        if (MAX_BUFFER_SIZE - total < nread) {
          throw new OutOfMemoryError("Required array size too large");
        }
        if (nread < buf.length) {
          buf = Arrays.copyOfRange(buf, 0, nread);
        }
        total += nread;
        if (result == null) {
          result = buf;
        } else {
          if (bufs == null) {
            bufs = new ArrayList<>();
            bufs.add(result);
          }
          bufs.add(buf);
        }
      }
      // if the last call to read returned -1 or the number of bytes
      // requested have been read then break
    } while (n >= 0 && remaining > 0);

    if (bufs == null) {
      if (result == null) {
        return new byte[0];
      }
      return result.length == total ?
          result : Arrays.copyOf(result, total);
    }

    result = new byte[total];
    int offset = 0;
    remaining = total;
    for (byte[] b : bufs) {
      int count = Math.min(b.length, remaining);
      System.arraycopy(b, 0, result, offset, count);
      offset += count;
      remaining -= count;
    }

    return result;
  }

  public static @NotNull ThreadFactory newThreadFactory(@NotNull String groupName) {
    return new DefaultThreadFactory(groupName);
  }

  private static class DefaultThreadFactory implements ThreadFactory {

    private ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public DefaultThreadFactory(String groupName) {
      assert groupName != null;
      group = new ThreadGroup(groupName);
      namePrefix = groupName + "-";
    }

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(group, r, namePrefix + threadNumber.getAndIncrement());
    }
  }

  private static long getBuildTime() {
    String buildTime = getBuildProperty("xyzhub.buildTime");
    try {
      return new SimpleDateFormat("yyyy.MM.dd-HH:mm").parse(buildTime).getTime();
    } catch (ParseException e) {
      return 0;
    }
  }

  protected static String getBuildProperty(String name) {
    InputStream input = AbstractHttpServerVerticle.class.getResourceAsStream("/build.properties");

    // load a properties file
    Properties buildProperties = new Properties();
    try {
      buildProperties.load(input);
    } catch (IOException ignored) {
    }

    return buildProperties.getProperty(name);
  }

  public static void die(final int exitCode, final @NotNull String reason) {
    die(exitCode, reason, new RuntimeException());
  }

  public static void die(
      final int exitCode,
      final @NotNull String reason,
      @Nullable Throwable exception
  ) {
    // Let's always generate a stack-trace.
    if (exception == null) {
      exception = new RuntimeException();
    }
    logger.error(reason, exception);
    System.out.flush();
    System.err.println(reason);
    exception.printStackTrace(System.err);
    System.err.flush();
    System.exit(exitCode);
  }
}