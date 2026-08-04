package naksha.base

import naksha.base.Base.BaseCompanion.INVALIDATED
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.base.JvmUtil.optimalObjectArrayLength
import java.lang.invoke.VarHandle
import java.util.Arrays
import java.util.concurrent.locks.ReentrantLock

/**
 * A map where key must be a [Literal].
 *
 * The implementation uses a [data] array to store key-value pairs aka entries. Each entry simply uses two elements in the [data] array. As the keys are all [Literal]'s, comparing keys can be done by reference. This means, searching for a key is an operation that purely runs on the L1 cache. The arrays are as well allocated in multiples of L1 cache lines. Therefore, access avoids [false sharing](https://en.wikipedia.org/wiki/False_sharing) and should be faster than standard hash-maps, as long as there are not too many entries. For the Naksha use case maps are expected to be plenty, but each quite small _(with no more than around 20 key-value pairs, often less than 5)_. Under these circumstances this implementation reduces the memory consumption due to the key-deduplication of [Literal], and finding keys is nearly as fast as direct access of [POJO](https://en.wikipedia.org/wiki/Plain_old_Java_object) fields, because we're actually only comparing memory addresses against each other and only access the L1 cache _(no dereferencing needed)_.
 *
 * When [atomic] is turned on, then the following algorithm is used to guarantee concurrent modifications of the map:
 *
 * - Always read/write key before value _(so lower memory addresses first)_.
 * - All keys can only be written ones, therefore, they only ever change from `null` to the key literal.
 * - Keys can occurr multiple times in the [data] array.
 * - The value can be anything, but ends in [Base.UNDEFINED] or [Base.INVALIDATED] states.
 * - Ones a value is [Base.UNDEFINED], it must be ignored, the entry is deleted. This is a tombstone state.
 * - Ones a value is [INVALIDATED], every operation reading this value must stop, wait until the invalidation is finished, then re-read the new `data` array and restart. All operations are design in that they can restart ones they find any [INVALIDATED] value.
 * - When the underlying `data` array is full, but new entries need to be created, an invalidation happens. Only one thread at a time can invalidate the array. Invalidation copies all valid values from the current `data` array into a new `data` array, skipping [Base.UNDEFINED] values. The algorithm will replace the actual value with [INVALIDATED], before copying it into the new array. When all values have been copied, the new `data` array becomes the actual `data` array. All pending algorithm will restart based upon the new `data` array.
 *
 * When being **not** [atomic], the general algorithm is the same, but the [INVALIDATED] state will not be used, because only one thread at a time is expected to modify the object. Therefore, invalidation is run inline and often will just copy data backwards in the array, so avoid allocation in invalidation.
 *
 * @since 3.0
 * @see Literal.of
 * @see Literal.literal
 */
actual class BaseMap actual constructor() : BaseObject(), IMutableMap {

    /**
     * The backing data array.
     *
     * Reading from array can be okay. Beware that only values not being [Base.UNDEFINED] or [Base.INVALIDATED] are valid entries. If the array contains a single value being [Base.INVALIDATED] the method [awaitInvalidation] must be invoked, which will block the current thread until the invalidation has been finished.
     *
     * The array contains key-value pairs, with keys stored at even indices and values add odd indices. The key is either `null` or a [Literal], nothing else is possible, therefore, the initial value of every entry is key being `null` _(unused entry)_ and ones the key was updated to a concrete `Literal`, this literal consumes the . The size of the array is the amount of elements not being [Base.UNDEFINED]. If the data is read in [atomic] mode, it is strongly recommended to make a copy of the data array to the stack before reading from it, this will ensure a snapshot.
     *
     * **The content of the array must not be modified!**
     *
     * @since 3.0
     */
    val data: Array<Any?>

    fun awaitInvalidation()

    private val invalidationLock = ReentrantLock()

    /**
     * Invalidates the current [data] array. Ensuring that eventually at least `need` amount of entries are available, when being successful.
     * @param data the data-array to invalidate.
     * @param undefiedCount the amount of entries that are in [Base.UNDEFINED] state in the given `data`-array.
     * @param need the amount of empty entries needed.
     * @return `true` if the [data] array was invalidated by this call; `false` if another call already invalidated the [data] array. Can only happen when [atomic] is `true` or the given `data` array is not the same as the current [data] array.
     * @since 3.0
     */
    private fun invalidate(data: Array<Any?>, undefiedCount: Int, need: Int): Boolean {
        if (!atomic) {
            if (data !== this.data) return false
            val new_data = if (undefiedCount >= need) data // Just a compaction needed.
                      else arrayOfNulls(optimalObjectArrayLength(data.size - (undefiedCount shl 1) + (need shl 1)))
            var from = 0
            var to = 0
            while (from < data.size) {
                val key = data[from]
                val value = data[from + 1]
                if (value !== UNDEFINED && key is Literal) {
                    new_data[to] = key
                    new_data[to + 1] = value
                    to += 2
                }
                from += 2
            }
            this.data = new_data
            return true
        }
        invalidationLock.lock()
        try {
            if (data !== this.data) return false // Another thread already invalidated
            val new_data = arrayOfNulls<Any?>(optimalObjectArrayLength(data.size - (undefiedCount shl 1) + (need shl 1)))
            var from = 0
            var to = 0
            while (from < data.size) {
                val key = data[from]
                VarHandle.loadLoadFence()
                val value = data[from + 1]
                if (key != null && value !== UNDEFINED && value !== INVALIDATED) {
                    if (!dataHandle.compareAndSet(data, from+1, value, INVALIDATED)) {
                        // Another thread modified the value before we were able to copy it.
                        // Simply repeat, we may find UNDEFINED this time.
                        continue
                    }
                    new_data[to] = key
                    new_data[to + 1] = value
                    to += 2
                }
                from += 2
            }
            this.data = new_data
            VarHandle.storeStoreFence()
            return true
        } finally {
            invalidationLock.unlock()
        }
    }

    /**
     * This function is internally called when any operation detects that the backing [data] array is invalidated while the operation was ongoing. It is invoked before the operation repeats.
     *
     * Invalidation means that the backing data array is potentially replaced with a new one, keys can be reordered. Actually, invalidation often means to compact the array to gain space for new keys. Invalidation only happens when [atomic] is `true`.
     */
    private fun waitForInvalidation() {
        if (!atomic) return
        // The data array is being invalidated, should only be called when atomic is true.
        // There can be potential modifications by another thread.
        // We wait for the invalidation to be finished.
        invalidationLock.lock()
        invalidationLock.unlock()
        // The lock and unlock will add full memory fences, so when we
        // restart the loop, the re-read of `data` should get the latest
        // state, which must not have an INVALIDATED entries.
    }

    actual override fun forEach(action: (key: Literal, value: Any?) -> Unit) {
        var startVersion = version
        var at = 0
        var atKey: Literal? = null
        var atValue: Any?
        repeat@ while (true) {
            val data = this.data
            var i = 0 // Index in data
            var pos = -1 // Valid entry position
            while (i < data.size) {
                val key = data[i] ?: return // The map ends on first key being null!
                if (atomic) VarHandle.loadLoadFence()
                else if (startVersion != version) {
                    // The data has been modified, we need to restart.
                    // In atomic mode the INVALIDATION value is used for this purpose.
                    startVersion = version
                    continue@repeat
                }
                val value = data[i+1]
                if (value === INVALIDATED) {
                    waitForInvalidation()
                    continue@repeat
                }
                if (value === UNDEFINED) {
                    i += 2
                    continue
                }
                pos++ // This is a valid entry
                if (pos < at) { // We are in a recovery mode.
                    if (key === atKey) {
                        // We found the key we last processed, so we will continue at the next valid,
                        // no matter what position we originally were at. The reason are concurrent
                        // map modifcations, they can delete keys before the key.
                        at = pos
                    }
                    // We are not yet where we should be, continue with next position.
                    i += 2
                    continue
                }
                at = pos
                atKey = key as Literal
                atValue = value
                try {
                    action(atKey, atValue)
                } catch (e: Exception) {
                    @Suppress("UNCHECKED_CAST") // Need to be checked by caller!
                    if (e is ForEachAbort) return e.value as? R?
                    if (e is NakshaException) throw e
                    throw generalException("Unexpected exception in forEach action handler: ${e.message}", e)
                }
                i += 2
            }
            // End of a full map.
            return
        }
    }

    actual override fun <R> reduce(initialValue: R?, action: (key: Literal, value: Any?, result: R?) -> R?): R? {
        var startVersion = version
        var at = 0
        var atKey: Literal? = null
        var atValue: Any?
        var atResult = initialValue
        repeat@ while (true) {
            val data = this.data
            var i = 0 // Index in data
            var pos = -1 // Valid entry position
            while (i < data.size) {
                val key = data[i] ?: return atResult // The map ends on first key being null!
                if (atomic) VarHandle.loadLoadFence()
                else if (startVersion != version) {
                    // The data has been modified, we need to restart.
                    // In atomic mode the INVALIDATION value is used for this purpose.
                    startVersion = version
                    continue@repeat
                }
                val value = data[i+1]
                if (value === INVALIDATED) {
                    waitForInvalidation()
                    continue@repeat
                }
                if (value === UNDEFINED) {
                    i += 2
                    continue
                }
                pos++ // This is a valid entry
                if (pos < at) { // We are in a recovery mode.
                    if (key === atKey) {
                        // We found the key we last processed, so we will continue at the next valid,
                        // no matter what position we originally were at. The reason are concurrent
                        // map modifcations, they can delete keys before the key.
                        at = pos
                    }
                    // We are not yet where we should be, continue with next position.
                    i += 2
                    continue
                }
                at = pos
                atKey = key as Literal
                atValue = value
                try {
                    atResult = action(atKey, atValue, atResult)
                } catch (e: Exception) {
                    @Suppress("UNCHECKED_CAST") // Need to be checked by caller!
                    if (e is ForEachAbort) return e.value as? R?
                    if (e is NakshaException) throw e
                    throw generalException("Unexpected exception in forEach action handler: ${e.message}", e)
                }
                i += 2
            }
            // End of a full map.
            return atResult
        }
    }

    actual override val length: Int
        get() {
            var startVersion = version
            repeat@ while (true) {
                val data = this.data
                var size = 0
                var i = 0
                while (i < data.size) {
                    data[i] ?: return size // The map ends on first key being null!
                    if (atomic) VarHandle.loadLoadFence()
                    else if (startVersion != version) {
                        // The data has been modified, we need to restart.
                        // In atomic mode the INVALIDATION value is used for this purpose.
                        startVersion = version
                        continue@repeat
                    }
                    val v = data[i+1]
                    if (v === INVALIDATED) {
                        waitForInvalidation()
                        continue@repeat
                    }
                    if (v !== UNDEFINED) size++
                    i += 2
                }
                // End of a full map.
                return size
            }
        }

    actual override operator fun get(key: Literal): Any? {
        var startVersion = version
        repeat@ while (true) {
            val data = this.data
            var i = 0
            while (i < data.size) {
                val k = data[i] ?: return null // The map ends on first key being null!
                if (atomic) VarHandle.loadLoadFence()
                else if (startVersion != version) {
                    // The data has been modified, we need to restart.
                    // In atomic mode the INVALIDATION value is used for this purpose.
                    startVersion = version
                    continue@repeat
                }
                val v = data[i+1]
                if (v === INVALIDATED) {
                    waitForInvalidation()
                    continue@repeat
                }
                if (k === key && v !== UNDEFINED) {
                    // A valid key found, return the value.
                    // Beware:
                    // We must not return when the value is UNDEFINED. The reason is
                    // that the key can appear multiple times in the data array.
                    // For example when adding the key, then deleting, then adding again.
                    // In that case the key would be two times in the array.
                    return v
                }
                i += 2
            }
            // end of a full map.
            return UNDEFINED
        }
    }

    actual override operator fun set(key: Literal, value: Any?) {
        if (value === UNDEFINED) {
            remove(key)
            return
        }
        if (value === INVALIDATED) throw illegalArg("value must not be INVALIDATED")
        startMutate()
        try {
            var startVersion = version
            repeat@ while (true) {
                val data = this.data
                var undefinedCount = 0
                var i = 0
                iterate@ while (i < data.size) {
                    val k = data[i]
                    if (k == null) {
                        if (!atomic) {
                            data[i] = key
                            data[i + 1] = value
                            return
                        }
                        if (dataHandle.compareAndSet(data, i, key)) {
                            dataHandle.compareAndSet(data, i + 1, value)
                            // We created the key, we may still fail to set the value.
                            // However, if we created the key, the value virtually/logically
                            // was created with the key. Now, another thread modified the value,
                            // actually between our key creation and then value setting.
                            // Still, we treat this as if we wrote the key and value, and then
                            // the other thread updated the value. So we can safely return here,
                            // no matter if the CAS above succeeds or not.
                            return
                        }
                        // Another thread modified the key, re-read the key and check what to do.
                        continue@iterate
                    }
                    if (atomic) VarHandle.loadLoadFence()
                    else if (startVersion != version) {
                        // The data has been modified, we need to restart.
                        // In atomic mode the INVALIDATION value is used for this purpose.
                        startVersion = version
                        continue@repeat
                    }
                    val v = data[i + 1]
                    if (v === INVALIDATED) {
                        if (atomic) waitForInvalidation()
                        continue@repeat
                    }
                    if (v === UNDEFINED) {
                        undefinedCount++
                        i += 2
                        continue
                    }
                    if (k === key) {
                        if (!atomic) {
                            data[i + 1] = value
                            return
                        }
                        var existing = v
                        while (true) {
                            if (dataHandle.compareAndSet(data, i + 1, existing, value)) {
                                // We successfully updated the value.
                                return
                            }
                            // Another thread updated the value.
                            // We can use plain read, because the CAS above will update our L1 cache
                            // and insert a fence, so we expect no reordering of the load before the CAS.
                            existing = data[i + 1]
                            if (existing === UNDEFINED) {
                                // The other thread deleted the entry, we need to move on.
                                undefinedCount++
                                i += 2
                                continue@iterate
                            }
                            if (existing === INVALIDATED) {
                                // An invalidation is ongoing, repeat from start.
                                waitForInvalidation()
                                continue@repeat
                            }
                            // The other thread updated the value, but as he was faster than us
                            // logically, we can override him perfectly fine, so retry to update
                            // the value. We just need to prevent that we ever override final
                            // states like UNDEFINED and INVALIDATED!
                        }
                    }
                    // key is not what we looked for, continue iteration
                    i += 2
                } // end iterate
                // If we reach this point, the key is not in the map, but there is as well no
                // empty entry. Let's invalidate and ensure that there is a free entry for us
                // to add our key. Then we repeat the operation.
                invalidate(data, undefinedCount, 3)
            } // end repeat
        } finally {
            endMutate()
        }
    }

    actual override fun containsKey(key: Literal): Boolean {
        var startVersion = version
        repeat@ while (true) {
            val data = this.data
            var i = 0
            while (i < data.size) {
                val k = data[i] ?: return false // map ends on first key being null!
                if (atomic) VarHandle.loadLoadFence()
                else if (startVersion != version) {
                    // The data has been modified, we need to restart.
                    // In atomic mode the INVALIDATION value is used for this purpose.
                    startVersion = version
                    continue@repeat
                }
                val v = data[i+1]
                if (v === INVALIDATED) {
                    waitForInvalidation()
                    continue@repeat
                }
                if (v !== UNDEFINED && k === key) return true // key found, return value.
                i += 2
            }
            // end of a full map.
            return false
        }
    }

    actual override fun delete(key: Literal): Any? {
        var startVersion = version
        startMutate()
        try {
            repeat@ while (true) {
                val data = this.data
                var i = 0
                while (i < data.size) {
                    val k = data[i] ?: return UNDEFINED // The map ends on first key being null!
                    if (atomic) VarHandle.loadLoadFence()
                    else if (startVersion != version) {
                        // The data has been modified, we need to restart.
                        // In atomic mode the INVALIDATION value is used for this purpose.
                        startVersion = version
                        continue@repeat
                    }
                    val v = data[i + 1]
                    if (v === INVALIDATED) {
                        waitForInvalidation()
                        continue@repeat
                    }
                    if (v !== UNDEFINED && k === key) {
                        if (!atomic) {
                            data[i + 1] = UNDEFINED
                            return v
                        }
                        var existing = v
                        while (true) {
                            if (dataHandle.compareAndSet(data, i + 1, existing, UNDEFINED)) {
                                // We successfully delete the entry.
                                return existing
                            }
                            // Another thread updated the value.
                            // We can use plain read, because the CAS above will update our L1 cache
                            // and insert a fence, so we expect no reordering of the load before the CAS.
                            existing = data[i + 1]
                            if (existing === UNDEFINED) {
                                // The other thread deleted the entry, fine for us
                                return UNDEFINED
                            }
                            if (existing === INVALIDATED) {
                                // An invalidation is ongoing, repeat from start.
                                waitForInvalidation()
                                continue@repeat
                            }
                            // The other thread updated the value, but as he was faster than us
                            // logically, we can override him perfectly fine, so retry to update
                            // the value.
                        }
                    }
                    i += 2
                }
                // end of a full map.
                return UNDEFINED
            }
        } finally {
            endMutate()
        }
    }

    actual override fun remove(key: Literal): Boolean = delete(key) !== UNDEFINED

    actual override fun setIfAbsent(key: Literal, value: Any?): Any? {
        if (value === UNDEFINED) throw illegalArg("value must not be UNDEFINED")
        if (value === INVALIDATED) throw illegalArg("value must not be INVALIDATED")
        startMutate()
        try {
            TODO("Implement me")
        } finally {
            endMutate()
        }
    }

    actual override fun compareAndSet(key: Literal, expectedValue: Any?, newValue: Any?): Boolean {
        if (expectedValue === UNDEFINED) throw illegalArg("expectedValue must not be UNDEFINED")
        if (expectedValue === INVALIDATED) throw illegalArg("expectedValue must not be INVALIDATED")
        if (newValue === UNDEFINED) throw illegalArg("newValue must not be UNDEFINED")
        if (newValue === INVALIDATED) throw illegalArg("newValue must not be INVALIDATED")
        startMutate()
        try {
            TODO("Implement me")
        } finally {
            endMutate()
        }
    }

    actual override fun deleteIf(key: Literal, expectedValue: Any): Any? {
        if (expectedValue === UNDEFINED) throw illegalArg("expectedValue must not be UNDEFINED")
        if (expectedValue === INVALIDATED) throw illegalArg("expectedValue must not be INVALIDATED")
        startMutate()
        try {
            TODO("Implement me")
        } finally {
            endMutate()
        }
    }

    actual override fun replace(key: Literal, expectedValue: Any, newValue: Any?): Boolean {
        if (expectedValue === UNDEFINED) throw illegalArg("expectedValue must not be UNDEFINED")
        if (expectedValue === INVALIDATED) throw illegalArg("expectedValue must not be INVALIDATED")
        if (newValue === UNDEFINED) throw illegalArg("newValue must not be UNDEFINED")
        if (newValue === INVALIDATED) throw illegalArg("newValue must not be INVALIDATED")
        startMutate()
        try {
            TODO("Implement me")
        } finally {
            endMutate()
        }
    }

    actual override fun removeIf(key: Literal, expectedValue: Any): Boolean {
        if (expectedValue === UNDEFINED) throw illegalArg("expectedValue must not be UNDEFINED")
        if (expectedValue === INVALIDATED) throw illegalArg("expectedValue must not be INVALIDATED")
        startMutate()
        try {
            TODO("Implement me")
        } finally {
            endMutate()
        }
    }

    actual override fun clear() {
        startMutate()
        try {
            if (!atomic) {
                Arrays.fill(this.data, null)
                return
            }
            repeat@ while (true) {
                val data = this.data
                var i = 0
                iterate@ while (i < data.size) {
                    if (data[i] == null) return // The map ends on first key being null!
                    VarHandle.loadLoadFence()
                    val v = data[i + 1]
                    if (v === INVALIDATED) {
                        waitForInvalidation()
                        continue@repeat
                    }
                    if (v !== UNDEFINED) {
                        // Key is alive, delete it.
                        if (!dataHandle.compareAndSet(data, i + 1, v, UNDEFINED)) {
                            // Another thread modified the value.
                            // Simply retry the same entry.
                            continue@iterate
                        }
                    }
                    // Done, this entry is UNDEFINED (deleted).
                    i += 2
                }
                return
            }
        } finally {
            endMutate()
        }
    }
}