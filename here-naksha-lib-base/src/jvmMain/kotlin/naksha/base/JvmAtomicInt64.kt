package naksha.base

import java.util.concurrent.atomic.AtomicLong

internal class JvmAtomicInt64 internal constructor(initialValue: Long): AtomicInt64 {

    internal constructor(value: Int64) : this(value.toLong())

    private val atomicLong: AtomicLong = AtomicLong(initialValue)

    override fun get(): Int64 =
        Int64(atomicLong.get())

    override fun set(value: Int64) =
        atomicLong.set(value.toLong())

    override fun compareAndSet(expect: Int64, update: Int64): Boolean =
        atomicLong.compareAndSet(expect.toLong(), update.toLong())

    override fun getAndAdd(value: Int64): Int64 =
        Int64(atomicLong.getAndAdd(value.toLong()))

    override fun addAndGet(value: Int64): Int64 =
        Int64(atomicLong.addAndGet(value.toLong()))
}