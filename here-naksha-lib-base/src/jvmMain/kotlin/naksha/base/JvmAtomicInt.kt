package naksha.base

import java.util.concurrent.atomic.AtomicInteger

class JvmAtomicInt internal constructor(initialValue: Int) : AtomicInteger(initialValue), AtomicInt {
    override fun toByte(): Byte = get().toByte()

    override fun toShort(): Short = get().toShort()
}