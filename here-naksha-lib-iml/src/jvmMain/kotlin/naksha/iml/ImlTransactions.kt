package naksha.iml

import naksha.base.Action
import naksha.base.Id
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.proxy
import naksha.base.unsupportedOp
import naksha.model.Tuple
import naksha.model.XyzNs
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaTx
import naksha.model.request.FeatureTuple
import naksha.model.request.PropertyFilter
import naksha.model.request.ReadTransactions
import naksha.model.request.SuccessResponse

/** Reads the optional IML transaction log as self-contained transaction tuples. @since 3.0 */
@Suppress("DEPRECATION")
internal object ImlTransactions {
  /** Uses the session connection, including a writer's sticky transaction, without creating control tables. @since 3.0 */
  fun read(session: ImlReadSession, request: ReadTransactions): SuccessResponse {
    if (request.catalogId != Id.ADMIN_CATALOG_TEXT || request.collectionId != Id.TRANSACTIONS_COL_TEXT) {
      throw illegalArg("IML transaction reads require the administrative transaction collection")
    }
    ImlReadSession.validateProperties(request.query.properties)
    val storage = session.storage
    val members = session.sessionMetadata.collectionById(Id.ADMIN_CATALOG_TEXT, Id.TRANSACTIONS_COL_TEXT)?.useMembers()
    val query = ImlQuery(request, storage.hardCap, members)
    val filters = request.resultFilters.toMutableList()
    if (request.query.properties != null && filters.none { it is PropertyFilter }) filters.add(PropertyFilter(request))
    val paged = request.returnHandle == true
    val candidateLimit = if (paged || filters.isNotEmpty()) storage.hardCap else query.limit
    val candidates = ArrayList<FeatureTuple>()
    val table = "${ImlMetadata.quote(storage.config.metadataSchema)}.iml_transactions"
    session.withConnection { connection ->
      val exists = connection.prepareStatement("SELECT to_regclass(?)").use { statement ->
        statement.setString(1, table)
        statement.executeQuery().use { rows -> rows.next() && rows.getString(1) != null }
      }
      if (!exists) throw unsupportedOp("IML transaction log is not available")
      // Supply the relational fields expected by ImlQuery without treating a transaction as an ordinary layer row.
      val xyz = "feature->'properties'->'@ns:com:here:xyz'"
      val json = "jsonb_set(feature, '{properties}', COALESCE(feature->'properties', '{}'::jsonb) || " +
        "jsonb_build_object('@ns:com:here:xyz', COALESCE($xyz, '{}'::jsonb) || " +
        "jsonb_build_object('createdAt', feature->'time', 'updatedAt', feature->'time', 'authorTs', feature->'time')))"
      val source = "(SELECT feature->>'id' AS id, source_version AS i, source_version AS version, " +
        "9223372036854775807::bigint AS next_version, 'I' AS operation, $xyz->>'author' AS author, $json AS jsondata, " +
        "CASE WHEN jsonb_typeof(feature->'geometry') = 'object' THEN ST_SetSRID(ST_GeomFromGeoJSON((feature->'geometry')::text),4326) END AS geo " +
        "FROM $table) AS transactions"
      connection.prepareStatement("SELECT version, jsondata FROM $source WHERE ${query.predicate} ORDER BY ${query.orderBy} LIMIT ?").use { statement ->
        statement.setInt(query.bind(statement), candidateLimit)
        statement.fetchSize = minOf(candidateLimit, 1000)
        statement.executeQuery().use { rows ->
          while (rows.next()) {
            val sequence = rows.getLong("version")
            val transaction = NakshaFeature.fromJson(rows.getString("jsondata")).proxy(NakshaTx::class)
            val transactionVersion = Version.manual(sequence, Action.VERSION)
            if (transaction.id != transactionVersion.toString()) throw illegalArg("IML transaction ID does not match its source version")
            transaction.featureType = "naksha.Tx"
            val xyz = transaction.properties.xyz
            xyz.setRaw(XyzNs.CREATED_AT, transaction.time)
            xyz.setRaw(XyzNs.UPDATED_AT, transaction.time)
            xyz.setRaw(XyzNs.AUTHOR_TS, transaction.time)
            val tn = TupleNumber(storage.number, Id.ADMIN_CATALOG_INT, Id.TRANSACTIONS_COL_INT, transactionVersion.number, ImlTuple.version(sequence, "I"))
            val tuple = ImlTuple.encode(transaction, tn, members = members)
            candidates.add(ImlFeatureTuple(tn).apply { ImlTuple.attach(this, tuple) })
          }
        }
      }
    }
    val result = ArrayList<FeatureTuple>()
    val snapshot = ArrayList<Tuple>()
    var snapshotBytes = 0L
    for (candidate in candidates) {
      var entry: FeatureTuple? = candidate
      for (index in filters.indices.reversed()) {
        val filter = filters[index] ?: continue
        entry = filter.filter(entry ?: break)
      }
      val accepted = entry ?: continue
      if (paged) {
        val feature = accepted.feature ?: continue
        val tuple = ImlTuple.encode(feature, accepted.tupleNumber, accepted.tuple?.nextVersion ?: Version.HEAD.number, members)
        snapshotBytes = storage.handles.addBytes(snapshotBytes, tuple)
        snapshot.add(tuple)
      } else {
        result.add(accepted)
        if (result.size >= query.limit) break
      }
    }
    return if (paged) storage.handles.create(snapshot, query.limit, session.options) else SuccessResponse().withFeatureTupleList(result)
  }
}
