package naksha.base

/**
 * A `ReentrantLock` is **owned** by the thread last successfully locking, but not yet unlocking it. A thread invoking [lock] will return, successfully acquiring the lock, when the lock is not owned by another thread. The method will return immediately if the current thread already owns the lock. This can be checked using methods [isHeldByCurrentThread], and [getHoldCount].
 *
 * The constructor for this class accepts an optional _fairness_ parameter. When set _true_, under contention, locks favor granting access to the longest-waiting thread. Otherwise this lock does not guarantee any particular access order. Programs using fair locks accessed by many threads may display lower overall throughput (i.e., are slower; often much slower) than those using the default setting, but have smaller variances in times to obtain locks and guarantee lack of starvation. Note however, that fairness of locks does not guarantee fairness of thread scheduling. Thus, one of many threads using a fair lock may obtain it multiple times in succession while other active threads are not progressing and not currently holding the lock.
 *
 * Also note that the untimed [tryLock] method does not honor the fairness setting. It will succeed if the lock is available even if other threads are waiting.
 *
 * It is recommended practice to _always_ immediately follow a call to [lock] with a `try` _(Java/JavaScript)_ or `use` _(Kotlin)_ block, and to _always_ immediately call [unlock] as the first statement in the finally block. This implementation supports the [close] method of [AutoCloseable], therefore the resource `try` or `use` are applicable.
 *
 * The usage of a lock with auto-close support:
 * ```kotlin
 * val foo = BaseLock()
 * foo.lock().use {
 *   // Do something with the lock.
 * }
 * if (foo.tryLock()) foo.use {
 *   // Do something with the lock.
 * }
 * ```
 * In Java:
 * ```java
 * class Demo() {
 *   var foo = new BaseLock();
 *   void demo() {
 *     try (var _ = foo.lock()) {
 *       // Do something with the lock.
 *     }
 *     if (foo.tryLock()) try (foo) {
 *       // Do something with the lock.
 *     }
 *   }
 * }
 * ```
 * @param fair if the lock should be fair; defaults to _false_. Beware that fairness is expensive and unless explicitly needed, should not be used _(the usage of [tryLock] can break fairness)_.
 * @since 3.0
 */
expect open class Lock(fair: Boolean = false): AutoCloseable {
    /**
     * Acquires the lock.
     *
     * Acquires the lock if it is not held by another thread and returns immediately, setting the lock hold count to one.
     *
     * If the current thread already holds the lock then the hold count is incremented by one and the method returns immediately.
     *
     * If the lock is held by another thread then the current thread becomes disabled for thread scheduling purposes and lies dormant until the lock has been acquired, at which time the lock hold count is set to one.
     * @return this.
     * @since 3.0
     */
    fun lock(): Lock

    /**
     * Acquires the lock only if it is not held by another thread at the time of invocation.
     *
     * Acquires the lock if it is not held by another thread and returns immediately with the value _true_, setting the lock hold count to one. Even when this lock has been set to use a fair ordering policy, a call to [tryLock] _will_ immediately acquire the lock if it is available, whether or not other threads are currently waiting for the lock. This "barging" behavior can be useful in certain circumstances, even though it breaks fairness. If you want to honor the fairness setting for this lock, then use [tryLock] with [waitMillis] and/or [waitNanos] greater than `0` which is almost equivalent.
     *
     * If the current thread already holds this lock then the hold count is incremented by one and the method returns _true_.
     *
     * If the lock is held by another thread then this method will return immediately, or after the given wait time, with the value _false_.
     *
     * @param waitMillis the amount of milliseconds to wait, defaults to `0`.
     * @param waitNanos the amount of nanoseconds to wait, defaults to `0`.
     * @return _true_ if the lock was free and was acquired by the current thread, or the lock was already held by the current thread; and _false_ otherwise.
     * @since 3.0
     */
    fun tryLock(waitMillis: Int = 0, waitNanos: Int = 0): Boolean

    /**
     * Attempts to release this lock.
     *
     * If the current thread is the holder of this lock then the hold count is decremented. If the hold count is now zero then the lock is released.
     *
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE], if the current thread does not hold this lock.
     * @since 3.0
     */
    fun unlock()

    /**
     * Queries the number of holds on this lock by the current thread.
     *
     * A thread has a hold on a lock for each lock action that is not matched by an unlock action.
     *
     * The hold count information is typically only used for testing and debugging purposes. For example, if a certain section of code should not be entered with the lock already held then we can assert that fact.
     *
     * @return the number of holds on this lock by the current thread, or zero if this lock is not held by the current thread.
     * @since 3.0
     */
    val holdCount: Int

    /**
     * Queries if this lock is held by the current thread.
     *
     * This method is typically used for debugging and testing. For example, a method that should only be called while a lock is held can assert that this is the case.
     *
     * It can also be used to ensure that a reentrant lock is used in a non-reentrant manner.
     *
     * @return _true_ if current thread holds this lock and _false_ otherwise.
     * @since 3.0
     */
    val isHeldByCurrentThread: Boolean

    /**
     * Queries if this lock is held by any thread. This method is designed for use in monitoring of the system state, not for synchronization control.
     *
     * @return _true_ if any thread holds this lock and _false_ otherwise.
     * @since 3.0
     */
    val isLocked: Boolean

    /**
     * Release the lock, same as [unlock].
     * @since 3.0
     * @see unlock
     */
    override fun close()
}