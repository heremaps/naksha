package naksha.base

import naksha.base.JvmUtil.optimalObjectArrayLength
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.text.Normalizer
import java.util.concurrent.locks.ReentrantLock

actual class Literal private actual constructor(string: String): CharSequence {
    // The Literal keeps a strong reference to the string.
    // Therefore, as long as there is a reference to the literal, the string is in memory too.
    actual override val length: Int = string.length
    actual override operator fun get(index: Int): Char = this.string[index]
    actual override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this.string.subSequence(startIndex, endIndex)
    // ---------------------------< END OF CharSequence >------ ------------------

    actual val weakRef: LiteralWeakRef = LiteralWeakRef(this)
    actual val string: String = string
    override fun toString(): String = this.string
    override fun hashCode(): Int = this.string.hashCode()
    // This is the main trick, equals does work by reference compare for literals!
    override fun equals(other: Any?): Boolean = this === other

    private class Level1 {
        val level2 = arrayOfNulls<Level2>(1024)
        operator fun get(hashCode: Int): Level2 {
            val index = hashCode and 1023 // bits 0..9
            var value = level2[index]
            if (value == null) {
                value = Level2()
                if (level2ArrayHandle.compareAndSet(this, index, null, value)) return value
                value = level2[index]!! // reading again will now return the instance created by another thread.
            }
            return value
        }
    }

    private class Level2 {
        val level3 = arrayOfNulls<Level3>(1024)
        operator fun get(hashCode: Int): Level3 {
            val index = (hashCode ushr 10) and 1023 // bits 10..19
            var value = level3[index]
            if (value == null) {
                value = Level3()
                if (level3ArrayHandle.compareAndSet(this, index, null, value)) return value
                value = level3[index]!! // reading again will now return the instance created by another thread.
            }
            return value
        }
    }

    private class Level3 {
        /** Lock to synchronize [array] resize */
        val copyLock: ReentrantLock = ReentrantLock()
        /** The actual cache line, can be resized on demand. Stores all literals with hashCode equal in the lower 20-bit */
        var array: Array<LiteralWeakRef?> = arrayOfNulls(optimalObjectArrayLength(1))

        /**
         * Try to copy the given array and add the given literal to it.
         * @param array the array that was used in [get], but that had no more empty place.
         * @param newLiteral the literal that should be added.
         * @return `true` if the literal was added, the caller _([get])_ can return the literal; `false` otherwise, another thread was faster and created a new array into which the given [newLiteral] need to be added. That means for calling [get] to restart the adding loop.
         */
        private fun copyAndAdd(array: Array<LiteralWeakRef?>, newLiteral: Literal): Boolean {
            // Doing a copy in multiple thread for the same cache page does not make any sense.
            // Therefore, we ensure that only one thread at a time resized.
            // We use a reentrant lock, because this works in all JDKs even with virtual threads.
            copyLock.lock()
            try {
                VarHandle.loadLoadFence()
                val currentArray = this.array
                if (currentArray !== array) {
                    // Someone else copied the array while we were waiting for the lock, the caller should retry.
                    return false
                }

                // We need this to ensure that literals are not collected while we copy them.
                val literalRefs = arrayOfNulls<Literal>(currentArray.size)
                var literal_i = 0
                val newArray = arrayOfNulls<LiteralWeakRef?>(optimalObjectArrayLength(array.size + 3)) // let's add room for 2 more
                var new_i = 0
                var i = 0
                while (i < array.size) {
                    val ref: LiteralWeakRef? = array[i]
                    val literal: Literal? = ref?.get()
                    if (ref == null || literal == null) {
                        if (!literalWeakRefArrayHandle.compareAndSet(array, i, ref, INVALIDATED)) {
                            i++
                        }
                        // Another thread modified the array (replaced this value) while we are copying, retry the same index.
                        continue
                    }
                    // The reference is still valid (and will stay valid as we now hold a strong reference)
                    // Copy it, but do not invalidate it in the old cache line, because readers still use it!
                    newArray[new_i++] = ref
                    literalRefs[literal_i++] = literal // We only copy a hard reference to avoid that GC collects while we copy!
                    i++
                }
                // Add the new literal.
                newArray[new_i] = newLiteral.weakRef
                if (arrayHandle.compareAndSet(this, currentArray, newArray)) return true
                // This must not happen, we have a lock, there must not be any other thread copying.
                throw internalError("Failed copyAndAdd call, we should have a lock, therefore the above CAS must work!")
            } finally {
                copyLock.unlock()
            }
        }

        /**
         * Search for the string given in [NFC][NormalizerForm.NFC] form in the cache for the [Literal], so the singleton.
         * @param newString the string to turn into a literal.
         * @return the [Literal] for the given string.
         * @since 3.0
         */
        operator fun get(newString: String): Literal {
            var newLiteral: Literal? = null
            root@while (true) {
                val array = this.array
                // Search for the given string, if it is cached already.
                // Remember the last empty slot, that we can use for insertion, when it is not found.
                // Count valid entries.
                // All algorithm will find the same slot, when iterating in parallel.
                var lastEmptyIndex = -1
                var lastEmptyRef: LiteralWeakRef? = null
                var i = -1
                while (++i < array.size) {
                    val ref = array[i]

                    // Another thread is copying the array.
                    if (ref == INVALIDATED) {
                        // This is a trick, acquiring the lock blocks our thread exactly until the copy is done.
                        copyLock.lock()
                        // As we do not need the lock, we release it directly after we got it.
                        copyLock.unlock()
                        // Restart, the lock acquire and release will as well add the correct memory fences.
                        // In the next iteration we have an updated cache line.
                        continue@root
                    }

                    // Empty.
                    if (ref == null) {
                        if (lastEmptyIndex < 0) {
                            lastEmptyIndex = i
                            lastEmptyRef = null
                        }
                        continue
                    }

                    // Dereference.
                    val literal: Literal? = ref.get()
                    val string: String? = literal?.string

                    // If the string is `null`, the entry is empty (weak-ref was garbage collected).
                    if (string == null) {
                        if (lastEmptyIndex < 0) {
                            lastEmptyIndex = i
                            lastEmptyRef = ref
                        }
                        continue
                    }

                    // Test if the string is what we want.
                    if (newString == string) return literal
                }

                // We didn't find the string, so no literal exists.
                if (newLiteral == null) newLiteral = Literal(newString)
                if (lastEmptyIndex >= 0) {
                    // We have an empty spot in the literal array, insert into it.
                    if (literalWeakRefArrayHandle.compareAndSet(array, lastEmptyIndex, lastEmptyRef, newLiteral.weakRef)) {
                        // We succeeded.
                        return newLiteral
                    }
                    // Another thread acquired the empty slot faster, we have to restart to find the next empty slot.
                    // It is clearly possible, that the other thread just added the literal we tried to add.
                    // If that happens, the next iteration will find it, and return the singleton the other thread added.
                    // The CAS operation before will add memory fences.
                    continue@root
                }
                // If there is no space left in the cache line, resize it and add the literal.
                if (copyAndAdd(array, newLiteral)) {
                    // Copy was successful.
                    return newLiteral
                }
                // Ups, another thread made a copy, we need to restart the copy loop and re-read the array.
                VarHandle.loadLoadFence()
            }
        }

        /**
         * Search the string given in [NFC][NormalizerForm.NFC] form in the cache if it is contained, return the [Literal], otherwise `null`.
         * @param searchString the string to search for.
         * @return the [Literal] for the given string or `null`, if the string is not in the cache.
         * @since 3.0
         */
        fun find(searchString: String): Literal? {
            val array = this.array
            for (i in array.indices) {
                val ref = array[i] ?: continue
                val literal: Literal = ref.get() ?: continue
                if (literal.string == searchString) return literal
            }
            return null
        }
    }

    actual companion object Literal_C {
        private val level2ArrayHandle: VarHandle = MethodHandles.arrayElementVarHandle(Level2::class.java)
        private val level3ArrayHandle: VarHandle = MethodHandles.arrayElementVarHandle(Level3::class.java)
        private val literalWeakRefArrayHandle: VarHandle = MethodHandles.arrayElementVarHandle(LiteralWeakRef::class.java)
        private val arrayHandle: VarHandle = MethodHandles
            .privateLookupIn(Level3::class.java, MethodHandles.lookup())
            .findVarHandle(Level3::class.java, "array", Array::class.java)
        private val INVALIDATED = LiteralWeakRef(null)
        private var cacheRoot = Level1()

        private fun toNfc(string: String): String
            = if (Normalizer.isNormalized(string, Normalizer.Form.NFC)) string
              else Normalizer.normalize(string, Normalizer.Form.NFC)

        @JvmStatic
        actual fun normalize(string: String): String {
            val s = toNfc(string)
            val hashCode = s.hashCode()
            val literal = cacheRoot[hashCode][hashCode].find(s)
            return literal?.string ?: s
        }

        @JvmStatic
        actual fun find(nfcString: String): Literal? {
            val hashCode = nfcString.hashCode()
            return cacheRoot[hashCode][hashCode].find(nfcString)
        }

        @JvmStatic
        actual fun of(string: String): Literal {
            val s = toNfc(string)
            val hashCode = s.hashCode()
            return cacheRoot[hashCode][hashCode][s]
        }

        @JvmStatic
        actual fun ofNfcString(nfcString: String): Literal {
            val hashCode = nfcString.hashCode()
            return cacheRoot[hashCode][hashCode][nfcString]
        }

        @JvmStatic
        actual fun literal(string: String): Literal = of(string)
    }
}