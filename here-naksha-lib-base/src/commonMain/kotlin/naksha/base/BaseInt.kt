package naksha.base

/**
 * A concurrent (atomic) integer.
 * @since 3.0
 */
expect class BaseInt(value: Int): BaseNumber {
    override fun withAtomic(enable: Boolean): BaseInt
    override fun withReadOnly(enable: Boolean): BaseInt
    override fun withImmutable(enable: Boolean): BaseInt
    fun get(): Int
    fun set(value: Int)
    fun compareAndSet(expect: Int, update: Int): Boolean
    fun getAndAdd(value: Int): Int
    fun addAndGet(value: Int): Int
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toLong(): Long
    override fun toInt(): Int
    override fun toShort(): Short
    override fun toByte(): Byte
}
