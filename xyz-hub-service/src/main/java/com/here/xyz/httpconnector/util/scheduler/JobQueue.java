/*
 * Copyright (C) 2017-2022 HERE Europe B.V.
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
package com.here.xyz.httpconnector.util.scheduler;

import com.here.xyz.httpconnector.util.jobs.Job;
import com.here.xyz.hub.Core;
import com.here.xyz.hub.Service;
import com.mchange.v3.decode.CannotDecodeException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JobQueue implements Runnable {

  protected static final Logger logger = LogManager.getLogger();

  private final ConcurrentHashMap<Job, Job> JOB_QUEUE = new ConcurrentHashMap<>();

  protected boolean commenced = false;
  protected ScheduledFuture<?> executionHandle;

  public static int CORE_POOL_SIZE = 30;

  protected final static ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(CORE_POOL_SIZE,
      Core.newThreadFactory("jobqueue"));

  protected abstract void process() throws InterruptedException, CannotDecodeException;

  protected abstract void validateJob(Job j);

  protected abstract void prepareJob(Job j);

  protected abstract void executeJob(Job j);

  protected abstract void finalizeJob(Job j0);

  protected abstract void failJob(Job j);

  public void addJob(@NotNull Job job) {
    if (JOB_QUEUE.putIfAbsent(job, job) == null) {
      logger.info("[{}] added to JobQueue!", job.getId());
    } else {
      logger.info("[{}] is already in the JobQueue!", job.getId());
    }
  }

  public void removeJob(@NotNull Job job) {
    if (JOB_QUEUE.remove(job, job)) {
      logger.info("[{}] removed from JobQueue!", job.getId());
    }
  }

  public @Nullable String checkRunningImportJobsOnSpace(@NotNull String targetSpaceId) {
    final Enumeration<Job> keysEnum = JOB_QUEUE.keys();
    try {
      while (keysEnum.hasMoreElements()) {
        final Job job = keysEnum.nextElement();
        assert job != null;
        if (targetSpaceId.equalsIgnoreCase(job.getTargetSpaceId())) {
          return job.getId();
        }
      }
    } catch (NoSuchElementException ignore) {
    }
    return null;
  }

  public ConcurrentHashMap<Job, Job> getQueue() {
    return JOB_QUEUE;
  }

  protected int queueSize() {
    return JOB_QUEUE.size();
  }

  /**
   * Begins executing the JobQueue processing - periodically and asynchronously.
   *
   * @return This check for chaining
   */
  public JobQueue commence() {
    if (!commenced) {
      logger.info("Start!");
      commenced = true;
      executionHandle = JobQueue.executorService.scheduleWithFixedDelay(this, 0, Service.get().config.JOB_CHECK_QUEUE_INTERVAL_SECONDS,
          TimeUnit.SECONDS);
    }
    return this;
  }

  @Override
  public void run() {
    try {
      JobQueue.executorService.submit(() -> {
        try {
          process();
        } catch (InterruptedException | CannotDecodeException ignored) {
          //Nothing to do here.
        }
      });
    } catch (Exception e) {
      logger.error("{}: Error when executing Job", this.getClass().getSimpleName(), e);
    }
  }
}
