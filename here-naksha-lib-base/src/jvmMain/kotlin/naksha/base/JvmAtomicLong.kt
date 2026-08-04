package naksha.base

import java.util.concurrent.atomic.AtomicLong

class JvmAtomicLong internal constructor(initialValue: Long): naksha.base.AtomicLong {

    private val atomicLong: AtomicLong = AtomicLong(initialValue)

    override fun get(): Long = atomicLong.get()

    override fun set(value: Long) = atomicLong.set(value)

    override fun compareAndSet(expect: Long, update: Long): Boolean =
        atomicLong.compareAndSet(expect, update)

    override fun getAndAdd(value: Long): Long =
        atomicLong.getAndAdd(value)

    override fun addAndGet(value: Long): Long =
        atomicLong.addAndGet(value)
}