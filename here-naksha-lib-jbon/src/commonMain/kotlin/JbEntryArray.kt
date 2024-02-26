@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.jbon

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Intermediate class with shared code for array and map.
 */
@Suppress("UNCHECKED_CAST")
@JsExport
abstract class JbEntryArray<SELF : JbEntryArray<SELF>> : JbStructMapper<SELF>() {
    /**
     * The current index in the entry list.
     */
    internal var index = -1

    /**
     * The length of the entry-array.
     */
    internal var length = Int.MAX_VALUE

    override fun clear(): SELF {
        super.clear()
        length = Int.MAX_VALUE
        index = -1
        dropEntry()
        return this as SELF
    }

    // Internally called to move in-front of the first entry.
    override fun reset(): SELF {
        super.reset()
        index = -1
        dropEntry()
        return this as SELF
    }

    /**
     * Moves the reader forward. If the [reader], after being moved, is located at an invalid position, returns false.
     * Otherwise, if the [reader] is positioned at a valid position, return true. This method must not update [length],
     * [index] or update the cache, it **must** only move the [reader] to the next value.
     * @return true if the position is valid after being moved; false if the position is now invalid.
     */
    internal abstract fun nextEntry() : Boolean

    /**
     * Load needed values from where the [reader] is currently positioned into cache.
     */
    internal abstract fun loadEntry()

    /**
     * Invalidate the entry cache.
     */
    internal abstract fun dropEntry()

    /**
     * Tests if the current position is valid.
     * @return true if the current position is valid; false otherwise.
     */
    fun ok() : Boolean {
        return index >= 0 && reader.view != null && reader.offset < encodingEnd
    }

    /**
     * Loads the first entry.
     * @return true if there is at least one entry; false if empty.
     */
    fun first() : Boolean {
        if (index == 0) {
            return true
        }
        dropEntry()
        index = 0
        reader.offset = encodingStart
        if (ok()) return true
        index = -1
        return false
    }

    /**
     * Moves the position forward. If the position is moved behind the content end, the position becomes invalid.
     * @return true if the position is valid after being moved; false if the position is now invalid.
     */
    fun next(): Boolean {
        if (index < 0) {
            return first()
        }
        if (index in 0..< length) {
            dropEntry()
            if (nextEntry()) {
                index++
                return true
            }
            // We found the end.
            length = index + 1
            reset()
        }
        return false
    }

    /**
     * Returns the length of the container in amount of entries. If the exact length is not yet known, it will detect the
     * real length and cache it, therefore the first call can be slower than further invocations.
     * @return The amount of entries (0 to n).
     */
    fun length(): Int {
        if (length == Int.MAX_VALUE) {
            // We expect that parse header already does this!
            if (contentSize() == 0) {
                length = 0
                return 0
            }
            val backup = index
            reader.setOffset(encodingStart)
            var len = if (reader.ok()) 1 else 0
            while (nextEntry()) {
                len++
            }
            length = len
            reader.offset = backup
        }
        return length
    }

    /**
     * Returns the current position.
     * @return The current or -1, if the position currently is invalid.
     */
    fun pos(): Int {
        return index
    }

    /**
     * Sets the position. If the position is below zero or larger/greater than the [length], the position becomes
     * invalid. If the container is empty, the position will always be invalid (-1).
     * @param pos The position to seek to.
     * @return this.
     */
    fun seek(pos: Int): SELF {
        // If the index does not change, do nothing
        if (index == pos) {
            return this as SELF
        }
        // If we are requested to set the index to an invalid index.
        if (pos < 0 || pos >= length) {
            reset()
            return this as SELF
        }
        var i : Int
        if (index < 0 || index > pos) {
            reset()
            i = 0
        } else {
            i = index
        }
        while (i < pos && nextEntry()) i++
        // If we did not reach the requested index, index becomes invalid.
        if (i < pos) {
           reset()
        } else {
            index = i
        }
        return this as SELF
    }
}