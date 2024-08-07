package naksha.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// FIXME TODO move it to proper library
/**
 * A lock that is kept alive as long as the application lives.
 * The lock is released when close() is called or when the JVM is shutdown or crashes.
 *
 * Additionally, the lock is stored in the internal lock table and therefore is visible for others.
 * It can be tagged and modified like a normal feature. The storage will ensure that the lock is kept alive as long as this object is not closed.
 *
 * All locks are by default (when first acquired) single locks, so only one owner allowed.
 * However, it is possible to increment the maxOwners property to create shared locks.
 * Beware that shared locks can cause conflicts, which are always resolved optimistically.
 */
@OptIn(ExperimentalJsExport::class, ExperimentalStdlibApi::class)
@JsExport
interface ILock: AutoCloseable {

    fun owner(): String

    fun copyFeature(): NakshaLock

    fun updateFeature(lock: NakshaLock)
}