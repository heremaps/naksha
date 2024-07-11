package naksha.base

import java.lang.ref.WeakReference

class JvmWeakRef<T: Any>(referent: T) : WeakReference<T>(referent), WeakRef<T> {
    override fun deref(): T? = super.get()
}