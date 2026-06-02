package naksha.jbon

/**
 * Optional callback used by [JbEncoder2] to externalize member values.
 *
 * Returning a value >= 0 signals that the encoder should write a members-book reference
 * for that index instead of encoding [value] inline. Returning -1 keeps normal encoding.
 */
fun interface IMemberEncoder {
    /**
     * @param path The current path segments (root is empty). The current key/index is at
     *             `path[pathEnd - 1]` when `pathEnd > 0`.
     * @param pathEnd The amount of valid path segments in [path].
     * @param value The value that is about to be encoded.
     * @return members-book index (>= 0) or -1 to continue normal encoding.
     */
    fun encode(path: Array<Any?>, pathEnd: Int, value: Any?): Int
}
