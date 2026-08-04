package naksha.base

/**
 * A concurrent (atomic) long.
 * @since 3.0
 */
expect class BaseDouble(value: Double): BaseNumber {
    override fun withAtomic(enable: Boolean): BaseDouble
    override fun withReadOnly(enable: Boolean): BaseDouble
    override fun withImmutable(enable: Boolean): BaseDouble
    fun get(): Double
    fun set(value: Double)
    fun compareAndSet(expect: Double, update: Double): Boolean
    fun getAndAdd(value: Double): Double
    fun addAndGet(value: Double): Double
    override fun toDouble(): Double
    override fun toFloat(): Float
    override fun toLong(): Long
    override fun toInt(): Int
    override fun toShort(): Short
    override fun toByte(): Byte
}
