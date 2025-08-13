package naksha.base

import java.util.concurrent.atomic.AtomicBoolean

internal class JvmAtomicBool internal constructor(initialValue: Boolean) : AtomicBoolean(initialValue), AtomicBool
