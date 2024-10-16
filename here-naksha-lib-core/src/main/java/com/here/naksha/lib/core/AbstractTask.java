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
package com.here.naksha.lib.core;

import com.here.naksha.lib.core.exceptions.TooManyTasks;
import com.here.naksha.lib.core.util.NanoTime;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A task that executes in an own dedicated thread to fulfill some job. The currently executed task can be queried using
 * {@link #currentTask()}.
 *
 * @param <RESULT> the response-type to be generated by the task.
 * @param <SELF>   the self-type.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public abstract class AbstractTask<RESULT, SELF extends AbstractTask<RESULT, SELF>>
    implements INakshaBound, UncaughtExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AbstractTask.class);

  private static IRequestLimitManager requestLimitManager = new DefaultRequestLimitManager();

  private String actor;

  public static void setConcurrencyLimitManager(IRequestLimitManager newRequestLimitManager) {
    requestLimitManager = newRequestLimitManager;
  }

  private static final ConcurrentHashMap<@NotNull String, @NotNull Long> actorUsageMap = new ConcurrentHashMap<>();
  private static final AtomicLong taskId = new AtomicLong(1L);
  private static final ThreadGroup allTasksGroup = new ThreadGroup("Naksha-Tasks");
  private static final ConcurrentHashMap<Long, NakshaWorker> allTasks = new ConcurrentHashMap<>();
  private static final @NotNull AtomicReference<Thread> shutdownHook = new AtomicReference<>();
  private static final ThreadPoolExecutor threadPool =
      (ThreadPoolExecutor) Executors.newCachedThreadPool(NakshaWorker::new);

  private static class NakshaWorker extends Thread {

    NakshaWorker(@NotNull Runnable runnable) {
      super(allTasksGroup, "NakshaWorker");
      setDaemon(true);
      this.runnable = runnable;
      id = taskId.getAndIncrement();
      setName(getName() + "#" + id);
      allTasks.put(id, this);

      Thread shutdownThread = shutdownHook.get();
      if (shutdownThread == null) {
        shutdownThread = new Thread(AbstractTask::shutdown);
        if (shutdownHook.compareAndSet(null, shutdownThread)) {
          Runtime.getRuntime().addShutdownHook(shutdownThread);
          log.info("Added shutdown hook to runtime");
        }
      }
    }

    final @NotNull Long id;
    final @NotNull Runnable runnable;

    @Override
    public void run() {
      try {
        log.atInfo().setMessage("Start new worker #{}").addArgument(id).log();
        runnable.run();
        log.atInfo()
            .setMessage("Worker #{} finished and going down")
            .addArgument(id)
            .log();
      } catch (Throwable t) {
        log.atError()
            .setMessage("Uncaught exception in Naksha worker")
            .setCause(t)
            .log();
      } finally {
        if (!allTasks.remove(id, this)) {
          log.atError()
              .setMessage("Failed to remove worker #{} from worker list")
              .addArgument(id)
              .log();
        }
      }
    }
  }

  private static void shutdown() {
    log.info("Start shutdown");
    try {
      try {
        log.info("Request thread pool to shutdown");
        threadPool.shutdown();
        if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
          log.warn("Failed to gracefully shutdown worker pool, do it now forcefully");
          threadPool.shutdownNow();
        } else {
          log.info("Thread pool reported that it is shutdown");
        }
      } catch (InterruptedException e) {
        log.warn("We got interrupted while waiting for the thread pool, force a shutdown now");
        threadPool.shutdownNow();
      }
    } catch (Throwable t) {
      log.atError()
          .setMessage("Failed to forceful shutdown of worker pool")
          .setCause(t)
          .log();
    }
    // Let's wait for the shutdown ones more.
    try {
      Thread.sleep(250);
    } catch (InterruptedException ignore) {
    }
    long aliveCount = 0;
    Enumeration<Long> keys = allTasks.keys();
    while (keys.hasMoreElements()) {
      try {
        final Long id = keys.nextElement();
        if (id == null) {
          continue;
        }
        final NakshaWorker worker = allTasks.get(id);
        if (worker == null) {
          continue;
        }
        if (worker.isAlive()) {
          aliveCount++;
          log.atError()
              .setMessage("Forcefully killing a worker that is still alive, this shouldn't ever happen")
              .setCause(new RuntimeException())
              .log();
          //noinspection removal
          worker.stop();
        }
      } catch (Throwable t) {
        log.atError()
            .setMessage("Fatal error, exception while forcefully killing worker")
            .setCause(t)
            .log();
      }
    }
    log.info("End of shutdown");
  }

  /**
   * Creates a new task.
   *
   * @param naksha  the reference to the Naksha host.
   * @param context the reference to the context.
   */
  public AbstractTask(@NotNull INaksha naksha, @NotNull NakshaContext context) {
    this.naksha = naksha;
    this.context = context;
    this.startNanos = NanoTime.now();
  }

  /**
   * Reference to the Naksha-Hub to which this task is bound.
   */
  private final @NotNull INaksha naksha;

  /**
   * The context bound to this task.
   */
  private final @NotNull NakshaContext context;

  /**
   * The time when the task was created.
   */
  private final long startNanos;

  @Override
  public final @NotNull INaksha naksha() {
    return naksha;
  }

  /**
   * Returns the {@link NakshaContext} to which this task is bound.
   *
   * @return the {@link NakshaContext} to which this task is bound.
   */
  public final @NotNull NakshaContext context() {
    return context;
  }

  /**
   * Returns the start time of the task in nanoseconds. This will differ from the {@link NakshaContext#startNanos()} time, the difference
   * can even be big, if this is just a child task.
   *
   * @return The start time of the task in nanoseconds.
   */
  public final long startNanos() {
    return startNanos;
  }

  /**
   * The uncaught exception handler for the thread that executes this task.
   *
   * @param thread the thread.
   * @param t      the exception.
   */
  public void uncaughtException(@NotNull Thread thread, @NotNull Throwable t) {
    log.atError()
        .setMessage("Uncaught exception in task {}")
        .addArgument(getClass().getName())
        .setCause(t)
        .log();
  }

  @SuppressWarnings("unchecked")
  protected final @NotNull SELF self() {
    return (SELF) this;
  }

  /**
   * A flag to signal that this task is internal.
   */
  private boolean internal;

  /**
   * Flag this task as internal, so when starting the task, the maximum amount of parallel tasks limit is ignored.
   *
   * @param internal {@code true} if this task is internal and therefore bypassing the maximum parallel tasks limit.
   * @throws IllegalStateException if the task is not in the state {@link State#NEW}.
   */
  public @NotNull SELF setInternal(boolean internal) {
    lockAndRequireNew();
    try {
      this.internal = internal;
    } finally {
      unlock();
    }
    return self();
  }

  /**
   * Tests whether this task flagged as internal.
   *
   * @return {@code true} if this task flagged as internal; {@code false} otherwise.
   */
  public boolean isInternal() {
    return internal;
  }

  /**
   * The thread to which this task is currently bound; if any.
   */
  @Nullable
  private Thread thread;

  /**
   * The previously set uncaught exception handler.
   */
  @Nullable
  private Thread.UncaughtExceptionHandler oldUncaughtExceptionHandler;

  @Nullable
  private String oldName;

  /**
   * Returns the task attached to the current thread; if any.
   *
   * @return The task attached to the current thread or {@code null}, if the current thread has no task attached.
   * @throws ClassCastException if the task is not of the expected type.
   */
  @SuppressWarnings("unchecked")
  public static <T extends AbstractTask<?, ?>> @Nullable T currentTask() {
    final Thread thread = Thread.currentThread();
    final UncaughtExceptionHandler uncaughtExceptionHandler = thread.getUncaughtExceptionHandler();
    if (uncaughtExceptionHandler instanceof AbstractTask<?, ?>) {
      return (T) uncaughtExceptionHandler;
    }
    return null;
  }

  /**
   * Returns the thread to which this task is currently bound; if any.
   *
   * @return The thread to which this task is currently bound; if any.
   */
  public @Nullable Thread getThread() {
    return thread;
  }

  /**
   * Binds this task to the current thread.
   *
   * @throws IllegalStateException if this task is bound to another thread, or the current thread is bound to another task.
   */
  public void attachToCurrentThread() {
    if (thread != null) {
      throw new IllegalStateException("Already bound to a thread");
    }
    final Thread thread = Thread.currentThread();
    final String threadName = thread.getName();
    final UncaughtExceptionHandler threadUncaughtExceptionHandler = thread.getUncaughtExceptionHandler();
    if (threadUncaughtExceptionHandler instanceof AbstractTask) {
      throw new IllegalStateException("The current thread is already bound to task " + threadName);
    }
    this.thread = thread;
    this.oldName = threadName;
    this.oldUncaughtExceptionHandler = threadUncaughtExceptionHandler;
    // thread.setName(context.getStreamId());
    thread.setUncaughtExceptionHandler(this);
    MDC.put("streamId", context.getStreamId());
  }

  /**
   * Removes this task form the current thread. The call will be ignored, if the task is unbound.
   *
   * @throws IllegalStateException If called from a thread to which this task is not bound.
   */
  public void detachFromCurrentThread() {
    if (this.thread == null) {
      return;
    }
    final Thread thread = Thread.currentThread();
    if (this.thread != thread) {
      throw new IllegalStateException("Can't unbind from foreign thread");
    }
    assert oldName != null;
    // thread.setName(oldName);
    thread.setUncaughtExceptionHandler(oldUncaughtExceptionHandler);
    this.thread = null;
    this.oldName = null;
    this.oldUncaughtExceptionHandler = null;
    MDC.remove("streamId");
  }

  /**
   * A lock to be used to modify the task thread safe.
   */
  private final ReentrantLock mutex = new ReentrantLock();

  /**
   * Acquire a lock, but only if the {@link #state()} is {@link State#NEW}.
   *
   * @throws IllegalStateException If the current state is not the one expected.
   */
  protected final void lockAndRequireNew() {
    mutex.lock();
    final State currentState = state.get();
    if (currentState != State.NEW) {
      mutex.unlock();
      throw new IllegalStateException("Found illegal state " + currentState.name() + ", expected NEW");
    }
  }

  /**
   * Unlocks a lock acquired previously via {@link #lockAndRequireNew()}.
   */
  protected final void unlock() {
    mutex.unlock();
  }

  /**
   * Creates a new thread, attach this task to the new thread, then call {@link #init()} followed by an invocation of {@link #execute()} to
   * generate the response.
   *
   * @return The future to the result.
   * @throws IllegalStateException If the {@link #state()} is not {@link State#NEW}.
   * @throws TooManyTasks          If too many tasks are executing already; not thrown for internal tasks.
   * @throws RuntimeException      If adding the task to the thread pool failed for an unknown error.
   */
  public @NotNull Future<@NotNull RESULT> start() {
    final long LIMIT = requestLimitManager.getInstanceLevelLimit();
    this.actor = context.getActor();
    lockAndRequireNew();
    try {
      final long ACTOR_LIMIT = requestLimitManager.getActorLevelLimit(context);
      incActorLevelUsage(this.actor, ACTOR_LIMIT);
      incInstanceLevelUsage(this.actor, LIMIT);
      try {
        state.set(State.START);
        final Future<RESULT> future = threadPool.submit(this::init_and_execute);
        return future;
      } catch (RejectedExecutionException e) {
        String errorMessage = "Maximum number of concurrent tasks (" + LIMIT + ") reached";
        decInstanceLevelUsage();
        decActorLevelUsage(this.actor);
        throw new TooManyTasks(errorMessage);
      } catch (Throwable t) {
        decInstanceLevelUsage();
        decActorLevelUsage(this.actor);
        log.atError()
            .setMessage("Unexpected exception while trying to fork a new thread")
            .setCause(t)
            .log();
        throw new RuntimeException("Internal error while forking new worker thread", t);
      }
    } finally {
      unlock();
    }
  }

  private static final AtomicLong threadCount = new AtomicLong();

  private @NotNull RESULT init_and_execute() {
    @NotNull RESULT RESULT;
    try {
      state.set(State.EXECUTE);
      attachToCurrentThread();
      init();
      RESULT = execute();

      state.set(State.CALLING_LISTENER);
      for (final @NotNull Consumer<@NotNull RESULT> listener : listeners) {
        try {
          listener.accept(RESULT);
        } catch (Throwable t) {
          log.atError()
              .setMessage("Uncaught exception in response listener")
              .setCause(t)
              .log();
        }
      }
    } catch (Throwable t) {
      RESULT = errorResponse(t);
    } finally {
      try {
        state.set(State.DONE);
        final long newValue = decInstanceLevelUsage();
        decActorLevelUsage(this.actor);
        assert newValue >= 0L;
        detachFromCurrentThread();
      } catch (Throwable t) {
        RESULT = errorResponse(t);
      }
    }
    return RESULT;
  }

  /**
   * Function should be overridden to return custom response when an exception is encountered during
   * execution of task functions init() / execute()
   *
   * @param throwable an actual error that has been encountered
   * @return RESULT should represent error response
   */
  protected @NotNull RESULT errorResponse(@NotNull Throwable throwable) {
    RESULT result = null;
    log.atWarn()
        .setMessage("The task failed with an exception")
        .setCause(throwable)
        .log();
    return result;
  }

  /**
   * Initializes this task.
   */
  protected abstract void init();

  /**
   * Execute this task.
   *
   * @return the response.
   */
  protected abstract @NotNull RESULT execute();

  /**
   * Try to cancel the task.
   *
   * @return {@code true} if the task cancelled successfully; {@code false} otherwise.
   */
  public boolean cancel() {
    return false;
  }

  /**
   * The state of the task.
   */
  public enum State {
    /**
     * The task is new.
     */
    NEW,

    /**
     * The task is starting.
     */
    START,

    /**
     * The task is executing.
     */
    EXECUTE,

    /**
     * Done executing and notifying listener.
     */
    CALLING_LISTENER,

    /**
     * Fully done.
     */
    DONE
  }

  private final AtomicReference<@NotNull State> state = new AtomicReference<>(State.NEW);

  /**
   * Returns the current state of the task.
   *
   * @return The current state of the task.
   */
  public final @NotNull State state() {
    return state.get();
  }

  private final @NotNull List<@NotNull Consumer<@NotNull RESULT>> listeners = new ArrayList<>();

  /**
   * Adds the given response listener.
   *
   * @param listener The listener to add.
   * @return {@code true} if added the listener; {@code false} if the listener already added.
   * @throws IllegalStateException If called after {@link #start()}.
   */
  public final boolean addListener(@NotNull Consumer<@NotNull RESULT> listener) {
    lockAndRequireNew();
    try {
      if (!listeners.contains(listener)) {
        listeners.add(listener);
        return true;
      }
      return false;
    } finally {
      state.set(State.NEW);
    }
  }

  /**
   * Remove the given response listener.
   *
   * @param listener The listener to remove.
   * @return {@code true} if removed the listener; {@code false} otherwise.
   * @throws IllegalStateException After {@link #start()} called.
   */
  public final boolean removeListener(@NotNull Consumer<@NotNull RESULT> listener) {
    lockAndRequireNew();
    try {
      // TODO HP_QUERY : Purpose of checking absence before removing?
      if (!listeners.contains(listener)) {
        listeners.remove(listener);
        return true;
      }
      return false;
    } finally {
      state.set(State.NEW);
    }
  }

  /**
   * Increments the value of instance level usage and compares with the specified limit.
   *
   * <p>This method ensures that the number of concurrent tasks for the instance
   * does not exceed the specified limit. If the limit is reached, it logs an
   * error and throws a {@link TooManyTasks} exception.
   *
   * @param actorId The identifier of the actor for which to acquire the slot.
   * @param limit The maximum number of concurrent tasks allowed for the instance.
   * @throws TooManyTasks If the maximum number of concurrent tasks is reached for the instance.
   */
  private void incInstanceLevelUsage(String actorId, long limit) {
    while (true) {
      final long threadCount = AbstractTask.threadCount.get();
      assert threadCount >= 0L;
      if (!internal && threadCount >= limit) {
        log.info(
            "NAKSHA_ERR_REQ_LIMIT_4_INSTANCE - [Request Limit breached for Instance => appId,author,actor,limit,crtValue] - ReqLimitForInstance {} {} {} {} {}",
            context.getAppId(),
            context.getAuthor(),
            actorId,
            limit,
            threadCount);
        String errorMessage = "Maximum number of concurrent tasks reached for instance (" + limit + ")";
        decActorLevelUsage(actorId);
        throw new TooManyTasks(errorMessage);
      }
      if (AbstractTask.threadCount.compareAndSet(threadCount, threadCount + 1)) {
        break;
      }
      // Failed, conflict, repeat
      log.info(
          "Concurrency conflict while incrementing instance level threadCount from {}. Will retry...",
          threadCount);
    }
  }

  private long decInstanceLevelUsage() {
    return AbstractTask.threadCount.decrementAndGet();
  }

  /**
   * Increments the value of author usage for given actor and compares with the specified limit.
   *
   * <p>This method ensures that the number of concurrent tasks for the actor
   * does not exceed the specified limit. If the limit is reached, it logs an
   * error and throws a {@link TooManyTasks} exception.
   *
   * @param actorId The identifier of the actor for which to acquire the slot.
   * @param limit The maximum number of concurrent tasks allowed for the actor.
   * @throws TooManyTasks If the maximum number of concurrent tasks is reached for the actor.
   */
  private void incActorLevelUsage(String actorId, long limit) {
    if (actorId == null) return;
    if (limit <= 0) {
      log.info(
          "NAKSHA_ERR_REQ_LIMIT_4_ACTOR - [Request Limit breached for Actor => appId,author,actor,limit,crtValue] - ReqLimitForActor {} {} {} {} {}",
          context.getAppId(),
          context.getAuthor(),
          actorId,
          limit,
          0);
      String errorMessage = "Maximum number of concurrent tasks reached for actor (" + limit + ")";
      throw new TooManyTasks(errorMessage);
    }
    while (true) {
      Long counter = actorUsageMap.get(actorId);
      if (counter == null) {
        Long existing = actorUsageMap.putIfAbsent(actorId, 1L);
        if (existing != null) {
          log.info(
              "Concurrency conflict while initializing threadCount to 1 for actorId [{}]. Will retry...",
              actorId);
          continue; // Repeat, conflict with other thread
        }
        return;
      }
      // Increment counter
      if (!internal && counter >= limit) {
        log.info(
            "NAKSHA_ERR_REQ_LIMIT_4_ACTOR - [Request Limit breached for Actor => appId,author,actor,limit,crtValue] - ReqLimitForActor {} {} {} {} {}",
            context.getAppId(),
            context.getAuthor(),
            actorId,
            limit,
            counter);
        String errorMessage = "Maximum number of concurrent tasks reached for actor (" + limit + ")";
        throw new TooManyTasks(errorMessage);
      }
      if (actorUsageMap.replace(actorId, counter, counter + 1)) {
        break;
      }
      // Failed, conflict, repeat
      log.info(
          "Concurrency conflict while incrementing actor level threadCount from {} for actorId [{}]. Will retry...",
          counter,
          actorId);
    }
  }

  /**
   * decrements the value of author usage given actor identifier.
   *
   * <p>This method decrements the usage count for the actor identifier. If the usage count
   * becomes zero, it removes the actor identifier from the map. If another thread attempts
   * to release the slot concurrently, it repeats the process until successful.
   *
   * @param actorId The identifier of the actor for which to release the slot.
   */
  private void decActorLevelUsage(String actorId) {
    if (actorId == null) return;
    while (true) {
      Long current = actorUsageMap.get(actorId);
      if (current == null) {
        log.error("Invalid actor usage value for actor: " + actorId + " value: null");
        break;
      } else if (current <= 1) {
        if (current <= 0) {
          log.error("Invalid actor usage value for actor: " + actorId + " value: " + current);
        }
        if (!actorUsageMap.remove(actorId, current)) {
          log.info(
              "Concurrency conflict while removing actor level threadCount for actorId [{}]. Will retry...",
              actorId);
          continue;
        }
        break;
      } else if (actorUsageMap.replace(actorId, current, current - 1)) {
        break;
      }
      // Failed, repeat, conflict with other thread
      log.info(
          "Concurrency conflict while decrementing actor level threadCount from {} for actorId [{}]. Will retry...",
          current,
          actorId);
    }
  }
}
