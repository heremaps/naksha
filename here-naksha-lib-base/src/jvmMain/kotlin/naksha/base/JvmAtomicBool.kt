package naksha.base

import java.util.concurrent.atomic.AtomicBoolean

class JvmAtomicBool internal constructor(initialValue: Boolean) : AtomicBoolean(initialValue), AtomicBool
