package naksha.base

import java.util.concurrent.atomic.AtomicReference

class JvmAtomicRef<R: Any>(initial: R?) : AtomicReference<R>(initial), AtomicRef<R>
