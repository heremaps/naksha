package naksha.iml

import java.util.UUID
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import naksha.base.illegalArg
import naksha.model.SessionOptions
import naksha.model.Tuple
import naksha.model.request.ReadHandle
import naksha.model.request.SuccessResponse

/** Storage-local, single-use continuations: at most 128 snapshots, 100000 tuples and 32 MiB accounted bytes, expiring five minutes after creation, including while idle. Each query builder is capped at 16 MiB independently; concurrent builders are not reserved storage-wide. Byte accounting charges four times serialized body bytes plus 512 bytes per tuple for member/object overhead, not measured JVM heap. No session, callback, or JDBC object is retained. @since 3.0 */
internal class ImlHandles(
  private val maxSnapshots: Int = 128,
  private val maxTuples: Int = 100000,
  private val ttlMillis: Long = 300000,
  private val maxBytes: Long = 32L * 1024 * 1024,
  private val maxSnapshotBytes: Long = 16L * 1024 * 1024,
  private val clock: () -> Long = { System.nanoTime() / 1000000 }
) {
  companion object {
    private val cleanup = ScheduledThreadPoolExecutor(1) { task ->
      Thread(task, "iml-handle-cleanup").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
  }

  private data class Snapshot(
    val tuples: List<Tuple>, val position: Int, val limit: Int,
    val appId: String, val authToken: String?, val expires: Long, val bytes: Long
  )
  private val snapshots = LinkedHashMap<String, Snapshot>()
  private var tupleCount = 0
  private var byteCount = 0L
  private var expiration: ScheduledFuture<*>? = null
  internal val retainedSnapshots: Int
    @Synchronized get() = snapshots.size

  fun addBytes(bytes: Long, tuple: Tuple): Long {
    val total = bytes + tuple.featureBytes.size.toLong() * 4 + 512
    if (total > maxSnapshotBytes) throw illegalArg("IML handle snapshot byte limit exceeded")
    return total
  }

  @Synchronized
  fun create(tuples: List<Tuple>, limit: Int, options: SessionOptions): SuccessResponse {
    expire()
    var bytes = 0L
    for (tuple in tuples) bytes = addBytes(bytes, tuple)
    if (tuples.size > limit && (snapshots.size >= maxSnapshots || tuples.size > maxTuples - tupleCount || bytes > maxBytes - byteCount)) {
      throw illegalArg("IML handle snapshot capacity exceeded")
    }
    return page(Snapshot(tuples.toList(), 0, limit, options.appId, options.authToken, clock() + ttlMillis, bytes), true)
  }

  @Synchronized
  fun read(request: ReadHandle, options: SessionOptions, hardCap: Int): SuccessResponse {
    expire()
    val snapshot = snapshots[request.handle]
    if (snapshot == null || snapshot.appId != options.appId || snapshot.authToken != options.authToken) {
      throw illegalArg("Invalid or expired IML handle")
    }
    val limit = request.limit ?: snapshot.limit
    if (limit <= 0) throw illegalArg("IML limit must be positive")
    if (request.resultFilters.isNotEmpty()) throw illegalArg("IML handles cannot change result filters")
    snapshots.remove(request.handle)
    tupleCount -= snapshot.tuples.size
    byteCount -= snapshot.bytes
    return page(snapshot.copy(limit = minOf(limit, hardCap)), request.returnHandle == true)
  }

  // SuccessResponse has no typed handle property: clients use getRaw("handle") as? String.
  private fun page(snapshot: Snapshot, returnHandle: Boolean): SuccessResponse {
    val end = minOf(snapshot.position.toLong() + snapshot.limit, snapshot.tuples.size.toLong()).toInt()
    val entries = ArrayList<naksha.model.request.FeatureTuple>(end - snapshot.position)
    for (index in snapshot.position until end) {
      val tuple = snapshot.tuples[index]
      entries.add(ImlFeatureTuple(tuple.tupleNumber).apply { ImlTuple.attach(this, tuple) })
    }
    val response = SuccessResponse().withFeatureTupleList(entries)
    if (returnHandle && end < snapshot.tuples.size) {
      val token = UUID.randomUUID().toString()
      snapshots[token] = snapshot.copy(position = end)
      tupleCount += snapshot.tuples.size
      byteCount += snapshot.bytes
      response.setRaw("handle", token)
    }
    scheduleExpiration()
    return response
  }

  @Synchronized
  internal fun expire() {
    val now = clock()
    val iterator = snapshots.values.iterator()
    while (iterator.hasNext()) {
      val snapshot = iterator.next()
      if (snapshot.expires <= now) {
        tupleCount -= snapshot.tuples.size
        byteCount -= snapshot.bytes
        iterator.remove()
      }
    }
    scheduleExpiration()
  }

  private fun scheduleExpiration() {
    expiration?.cancel(false)
    expiration = null
    if (snapshots.isEmpty()) return
    var earliest = Long.MAX_VALUE
    for (snapshot in snapshots.values) earliest = minOf(earliest, snapshot.expires)
    val owner = WeakReference(this)
    expiration = cleanup.schedule({ owner.get()?.expire() }, maxOf(1, earliest - clock()), TimeUnit.MILLISECONDS)
  }

  @Synchronized
  fun clear() {
    snapshots.clear()
    tupleCount = 0
    byteCount = 0
    expiration?.cancel(false)
    expiration = null
  }
}
