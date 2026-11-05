package naksha.psql

import naksha.base.Id
import naksha.base.PlatformUtil
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.jbon.HeapBook
import naksha.model.Tuple
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers.StandardMembers_C.NEXT_VERSION
import naksha.model.request.*
import kotlin.jvm.JvmField

class PgReader(
    /**
     * The session to which this reader is linked.
     */
    session: PgSession,

    /**
     * The read request to [execute].
     */
    @JvmField val request: ReadRequest,
) : PgReaderWriterBase(session) {
    /**
     * The storage.
     */
    val storage: PgStorage = session.storage

    /**
     * The version of which this reader is part.
     */
    val version: Version
        get() = session.useTransaction().version

    fun execute(): Response {
        val session = this.session
        val query: PgQuery = PgQueryBuilder(session, request).build()
        val history = when (val request = request) {
            is ReadFeatures -> request.queryHistory
            is ReadCollections -> request.toReadFeatures().queryHistory
            is ReadMaps -> request.toReadFeatures().queryHistory
            else -> false
        }
        return session.useConnNonNull<Response>(readOnly = true) { conn ->
            query.pgCatalog.applySearchPath(conn)
            if (PlatformUtil.ENABLE_INFO) {
                if (session.logQueries) {
                    session.logAtInfo(query.sql)
                }
                if (session.logExplain) {
                    val explain = session.explain(conn, false, query.sql, query.argTypes, query.argValues)
                    session.logAtInfo(explain)
                }
            }
            conn.prepare(query.sql, query.argTypes).use { plan ->
                // Start allocating around 8 KiB
                val featureTuples = FeatureTupleList()
                featureTuples.setCapacity(1024)
                // Reference columns only; history adds one bigint for successor metadata.
                plan.setFetchSize(1_000_000) // Set very high to encourage parallelism on server side
                // https://www.cybertec-postgresql.com/en/parallel-query-postgresql-problems-jdbc-dbeaver/
                plan.execute(query.argValues).use { cursor ->
                    val storageNumber = query.pgStorage.id.number
                    val mapNumber = query.pgCatalog.id.intValue
                    val collectionNumber = query.pgCollection.id.intValue
                    while (cursor.next()) {
                        val col_num: Int = collectionNumber ?: cursor["col_num"]
                        val fn = Id.ordinalToFeatureNumber(cursor.get<Long>("fn"))
                        val version: Long = cursor["version"]
                        val tn = TupleNumber(storageNumber, mapNumber, col_num, fn, version)
                        val reference = if (history) PgHistoryFeatureTuple(tn, cursor.get<Long>(NEXT_VERSION)) else FeatureTuple(tn)
                        featureTuples.add(reference)
                    }
                }
                return@use SuccessResponse().withFeatureTupleList(featureTuples)
            }
        }
    }
}

/** A history reference retains its selected successor even when the payload is loaded later in another session. @since 3.0 */
private class PgHistoryFeatureTuple(tn: TupleNumber, private val nextVersion: Long) : FeatureTuple(tn) {
    init {
        onTupleLoaded()
    }

    override var feature: NakshaFeature?
        get() {
            onTupleLoaded()
            return super.feature
        }
        set(value) {
            super.feature = value
        }

    override fun onTupleLoaded() {
        val cached = tuple ?: return
        if (cached.nextVersion == nextVersion) return
        // Never publish snapshot-specific metadata to the shared cache; the successor may be rolled back.
        val members = HeapBook.copyOf(cached.membersBook)
        members.put(NEXT_VERSION, nextVersion)
        tuple = Tuple(cached.featureBytes, members)
    }
}
