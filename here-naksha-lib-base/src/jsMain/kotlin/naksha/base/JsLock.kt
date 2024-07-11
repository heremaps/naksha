package naksha.base

// JavaScript is single threaded, therefore a reentrant lock is useless, we simply make a dummy implementation.
class JsLock : PlatformLock {
    override fun acquire(): PlatformLock = this

    override fun tryAcquire(waitMillis: Int64?): Boolean = true

    override fun close() {}
}