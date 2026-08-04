package naksha.base

/**
 * A concurrent (atomic) long.
 * @since 3.0
 */
expect class BaseLong(value: Long): BaseNumber {
    override fun withAtomic(enable: Boolean): BaseLong
    override fun withReadOnly(enable: Boolean): BaseLong
    override fun withImmutable(enable: Boolean): BaseLong
    fun get(): Long
    fun set(value: Long)
    fun compareAndSet(expect: Long, update: Long): Boolean
    fun getAndAdd(value: Long): Long
    fun addAndGet(value: Long): Long
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toLong(): Long
    override fun toInt(): Int
    override fun toShort(): Short
    override fun toByte(): Byte
}
