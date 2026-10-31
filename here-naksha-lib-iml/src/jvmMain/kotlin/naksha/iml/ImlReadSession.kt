package naksha.iml

import naksha.base.unsupportedOp
import naksha.base.Id
import naksha.base.TupleNumber
import naksha.base.illegalArg
import naksha.base.NakshaException
import naksha.base.NakshaError
import naksha.model.Naksha
import naksha.model.Tuple
import naksha.model.FETCH_ALL
import naksha.model.NO_CACHE_BIT
import naksha.model.IReadSession
import naksha.model.SessionOptions
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadRequest
import naksha.model.request.Response
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadMaps
import naksha.model.request.ReadCollections
import naksha.model.request.ReadHandle
import naksha.model.request.ReadTransactions
import naksha.model.request.PropertyFilter
import naksha.model.request.SuccessResponse
import naksha.model.objects.NakshaFeature
import java.sql.Connection
import java.util.Collections
import java.util.IdentityHashMap
import naksha.model.request.query.IPropertyQuery
import naksha.model.request.query.PAnd
import naksha.model.request.query.POr
import naksha.model.request.query.PNot
import naksha.model.request.query.PQuery

/**
 * An IML read session. Connections are opened per read and always rolled back and closed afterward.
 * @since 3.0
 */
@Suppress("DEPRECATION")
open class ImlReadSession internal constructor(
  override val storage: ImlStorage,
  options: SessionOptions
) : IReadSession {
  final override var options: SessionOptions = options
    private set
  @Volatile
  private var closed = false
  internal open val sessionMetadata: ImlMetadata get() = storage.metadata
  protected open val useSharedCache: Boolean get() = true
  protected open val transactional: Boolean get() = false

  init {
    require(options.connectTimeout >= 0 && options.socketTimeout >= 0 && options.stmtTimeout >= 0 && options.lockTimeout >= 0) {
      "IML session timeouts must not be negative"
    }
  }

  override var socketTimeout: Int
    get() = options.socketTimeout
    @Synchronized set(value) {
      checkOpen()
      require(value >= 0) { "socketTimeout must not be negative" }
      options = options.copyWithTimeouts(socketTimeout = value)
    }

  override var stmtTimeout: Int
    get() = options.stmtTimeout
    @Synchronized set(value) {
      checkOpen()
      require(value >= 0) { "stmtTimeout must not be negative" }
      options = options.copyWithTimeouts(stmtTimeout = value)
    }

  override var lockTimeout: Int
    get() = options.lockTimeout
    @Synchronized set(value) {
      checkOpen()
      require(value >= 0) { "lockTimeout must not be negative" }
      options = options.copyWithTimeouts(lockTimeout = value)
    }

  protected fun checkOpen() {
    check(!closed) { "IML read session is closed" }
  }

  @Synchronized
  internal open fun <T> withConnection(block: (Connection) -> T): T {
    checkOpen()
    return storage.withConnection(options, block)
  }

  @Synchronized
  override fun executeRead(request: ReadRequest): Response {
    checkOpen()
    if (!transactional && storage.config.writeEnabled) storage.refreshMetadata(options)
    if ((request.fetchMode and NO_CACHE_BIT.inv()) != FETCH_ALL) throw unsupportedOp("IML supports only fetching complete tuples")
    if (request is ReadHandle) {
      if (transactional) throw unsupportedOp("IML transaction-local reads cannot use shared continuation handles")
      return storage.handles.read(request, options, storage.hardCap)
    }
    if (transactional && request.returnHandle == true) throw unsupportedOp("IML uncommitted reads cannot publish continuation handles")
    if (request is ReadFeatures) {
      validateProperties(request.query.properties)
      if (request.query.properties != null && request.resultFilters.none { it is PropertyFilter }) request.refreshPropertyFilter()
    }
    val metadata = sessionMetadata
    return when (request) {
      is ReadTransactions -> ImlTransactions.read(this, request)
      is ReadMaps -> metadataResponse(metadata.catalogsById.values.filter { it.id != Id.ADMIN_CATALOG_TEXT }.map { metadata.synthetic.getValue(it.tupleNumber!!) }, request.mapIds, request)
      is ReadCollections -> {
        val catalog = getCatalogById(request.mapId ?: throw illegalArg("Missing catalog ID"))
          ?: throw NakshaException(NakshaError.MAP_NOT_FOUND, "IML catalog not found")
        val tuples = ArrayList<Tuple>()
        for (tuple in metadata.synthetic.values) {
          if (tuple.tupleNumber.catalogNumber == catalog.catalogNumber && tuple.tupleNumber.collectionNumber == Id.COLLECTIONS_COL_INT) tuples.add(tuple)
        }
        metadataResponse(tuples, request.collectionIds, request)
      }
      is ReadFeatures -> {
        val catalogId = request.catalogId ?: throw illegalArg("Missing catalog ID")
        val collectionId = request.collectionId ?: throw illegalArg("Missing collection ID")
        val query = ImlQuery(request, storage.hardCap, metadata.collectionById(catalogId, collectionId)?.useMembers())
        val catalog = getCatalogById(catalogId)
          ?: throw NakshaException(NakshaError.MAP_NOT_FOUND, "IML catalog not found")
        if ((catalogId == Id.ADMIN_CATALOG_TEXT && collectionId == Id.CATALOGS_COL_TEXT) || collectionId == Id.COLLECTIONS_COL_TEXT) {
          if (request.queryHistory || request.minVersion != null || request.version != null || request.versions != 1) {
            throw unsupportedOp("IML metadata has no revision history")
          }
          // Metadata has no physical layer table. Support the standard list/ID read surface only.
          if (request.queryMembers != null || request.query.tags != null || request.query.spatial != null || request.query.refTiles.isNotEmpty() || request.guids.isNotEmpty() || request.orderBy != null) {
            throw unsupportedOp("IML metadata supports only listing and legacy ID selection")
          }
          val tuples = ArrayList<Tuple>()
          for (tuple in metadata.synthetic.values) {
            if (tuple.tupleNumber.catalogNumber == catalog.catalogNumber && tuple.tupleNumber.collectionNumber == Id.textToInt(collectionId)) tuples.add(tuple)
          }
          metadataResponse(tuples, request.featureIds, request)
        } else {
          val layer = metadata.getLayer(catalogId, collectionId)
            ?: throw NakshaException(NakshaError.COLLECTION_NOT_FOUND, "IML layer not found")
          val tuples = ArrayList<FeatureTuple>()
          val candidateLimit = if (request.returnHandle == true || request.resultFilters.isNotEmpty()) storage.hardCap else query.limit
          withConnection { connection ->
            connection.prepareStatement(query.history.select(layer.sqlTable, query.predicate, query.orderBy) + " LIMIT ?").use { statement ->
              val next = query.bind(statement)
              statement.setInt(next, candidateLimit)
              statement.fetchSize = minOf(candidateLimit, 1000)
              statement.executeQuery().use { rows ->
                while (rows.next()) {
                  val tn = TupleNumber(storage.number, catalog.catalogNumber, layer.number, rows.getLong("i"), ImlTuple.version(rows.getLong("version"), rows.getString("operation")))
                  tuples.add(ImlFeatureTuple(tn))
                }
              }
            }
          }
          resultResponse(tuples, request, query.limit)
        }
      }
      else -> throw unsupportedOp("Unsupported IML read request: ${request::class.simpleName}")
    }
  }

  private fun metadataResponse(tuples: List<Tuple>, ids: List<String?>, request: ReadRequest): SuccessResponse {
    val limit = request.limit ?: storage.hardCap
    if (limit <= 0) throw illegalArg("IML limit must be positive")
    val result = ArrayList<FeatureTuple>()
    for (tuple in tuples.sortedBy { it.id }) {
      if (ids.isNotEmpty() && !ids.contains(tuple.id)) continue
      if (result.size >= storage.hardCap) break
      result.add(ImlFeatureTuple(tuple.tupleNumber).apply { ImlTuple.attach(this, tuple) })
    }
    return resultResponse(result, request, minOf(limit, storage.hardCap))
  }

  /** Filters scan at most hardCap candidates, materialized in batches of 128 after selection closes JDBC. @since 3.0 */
  private fun resultResponse(candidates: List<FeatureTuple>, request: ReadRequest, limit: Int): SuccessResponse {
    val paged = request.returnHandle == true
    val filters = request.resultFilters.toList()
    val bypassCache = (request.fetchMode and NO_CACHE_BIT) != 0 || !useSharedCache
    if (!paged && filters.isEmpty() && !bypassCache) {
      return SuccessResponse().withFeatureTupleList(candidates.take(limit))
    }
    val result = ArrayList<FeatureTuple>()
    val snapshot = ArrayList<Tuple>()
    var snapshotBytes = 0L
    var start = 0
    while (start < candidates.size && (paged || result.size < limit)) {
      val end = minOf(start + 128, candidates.size)
      loadExact(candidates, start, end, bypassCache)
      for (index in start until end) {
        var entry: FeatureTuple? = candidates[index]
        // Never retry a missing exact state via response.features and the lazy cache loader.
        if (entry?.tuple == null) continue
        for (filterIndex in filters.indices.reversed()) {
          val filter = filters[filterIndex] ?: continue
          entry = filter.filter(entry ?: break)
        }
        val accepted = entry ?: continue
        if (paged) {
          val feature = accepted.feature ?: continue
          val tuple = ImlTuple.encode(feature, accepted.tupleNumber, accepted.tuple?.nextVersion ?: naksha.base.Version.HEAD.number)
          snapshotBytes = storage.handles.addBytes(snapshotBytes, tuple)
          snapshot.add(tuple)
        } else {
          result.add(accepted)
          if (result.size >= limit) break
        }
      }
      start = end
    }
    return if (paged) storage.handles.create(snapshot, limit, options) else SuccessResponse().withFeatureTupleList(result)
  }

  companion object {
    /** Bound recursion before the built-in PropertyFilter visits a legacy property query. @since 3.0 */
    internal fun validateProperties(query: IPropertyQuery?) {
      val path = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
      var nodes = 0
      fun visit(query: IPropertyQuery?, depth: Int) {
        if (query == null) throw illegalArg("IML property query contains a null child")
        val identity = when (query) {
          is PAnd -> query.platformObject()
          is POr -> query.platformObject()
          is PNot -> query.platformObject()
          is PQuery -> query.platformObject()
          else -> throw illegalArg("Unknown IML property query")
        }
        if (depth > 64 || ++nodes > 1024 || !path.add(identity)) throw illegalArg("IML property query is cyclic, too deep, or too large")
        try {
          when (query) {
            is PAnd -> for (child in query) visit(child, depth + 1)
            is POr -> for (child in query) visit(child, depth + 1)
            is PNot -> visit(query.query, depth + 1)
            is PQuery -> Unit
          }
        } finally {
          path.remove(identity)
        }
      }
      if (query != null) visit(query, 0)
    }
  }

  override fun getCatalogById(catalogId: String, allowTombstone: Boolean): NakshaCatalog? {
    checkOpen()
    val metadata = sessionMetadata
    val catalog = metadata.getCatalogById(catalogId) ?: return null
    val tuple = metadata.synthetic[catalog.tupleNumber] ?: return null
    return tuple.decodeFeature(null).proxy(NakshaCatalog::class)
  }

  override fun getCatalogByNumber(catalogNumber: Int, allowTombstone: Boolean): NakshaCatalog? {
    checkOpen()
    val metadata = sessionMetadata
    return metadata.getCatalogByNumber(catalogNumber)?.let { getCatalogById(it.id) }
  }

  override fun getCollectionById(catalog: NakshaCatalog, collectionId: String, allowTombstone: Boolean): NakshaCollection? {
    checkOpen()
    if (catalog.databaseNumber != storage.number) return null
    val metadata = sessionMetadata
    val collection = metadata.collectionById(catalog.id, collectionId) ?: return null
    val tuple = metadata.synthetic[collection.tupleNumber] ?: return null
    return tuple.decodeFeature(null).proxy(NakshaCollection::class)
  }

  override fun getCollectionByNumber(catalog: NakshaCatalog, collectionNumber: Int, allowTombstone: Boolean): NakshaCollection? {
    checkOpen()
    val metadata = sessionMetadata
    val id = when (collectionNumber) {
      Id.CATALOGS_COL_INT -> Id.CATALOGS_COL_TEXT
      Id.COLLECTIONS_COL_INT -> Id.COLLECTIONS_COL_TEXT
      else -> metadata.getLayerByNumber(catalog.catalogNumber, collectionNumber)?.id ?: return null
    }
    return getCollectionById(catalog, id)
  }

  @Synchronized
  override fun loadTuples(featureTuples: List<FeatureTuple?>, from: Int, to: Int) {
    loadExact(featureTuples, from, to, false)
  }

  private fun loadExact(featureTuples: List<FeatureTuple?>, from: Int, to: Int, bypassCache: Boolean) {
    checkOpen()
    require(from >= 0 && to >= from && to <= featureTuples.size) { "Invalid tuple loading range" }
    val snapshot = if (useSharedCache) storage.discoverySnapshot() else sessionMetadata to storage.generation
    val metadata = snapshot.first
    val missing = LinkedHashMap<Pair<Int, Int>, LinkedHashMap<TupleNumber, MutableList<FeatureTuple>>>()
    val cacheGeneration = snapshot.second
    for (index in from until to) {
      val target = featureTuples[index] ?: continue
      val tn = target.tupleNumber
      if (tn.databaseNumber != storage.number) continue
      val cached = if (bypassCache || !useSharedCache) metadata.synthetic[tn] else target.tuple ?: metadata.synthetic[tn] ?: storage.cachedTuple(tn)
      if (cached != null) {
        ImlTuple.attach(target, cached)
        continue
      }
      if (metadata.getLayerByNumber(tn.catalogNumber, tn.collectionNumber) == null) continue
      missing.getOrPut(tn.catalogNumber to tn.collectionNumber) { LinkedHashMap() }.getOrPut(tn) { ArrayList() }.add(target)
    }
    if (missing.isEmpty()) return
    withConnection { connection ->
      for ((location, targets) in missing) {
        val layer = checkNotNull(metadata.getLayerByNumber(location.first, location.second))
        val numbers = targets.keys.toList()
        var start = 0
        while (start < numbers.size) {
          val end = minOf(start + 500, numbers.size)
          val predicates = ArrayList<String>()
          for (index in start until end) predicates.add("(i = ? AND version = ?)")
          connection.prepareStatement("SELECT id, i, version, next_version, operation, author, jsondata, ST_AsGeoJSON(geo, 15, 0) AS geometry_json FROM ${layer.sqlTable} WHERE ${predicates.joinToString(" OR ")}").use { statement ->
            var parameter = 1
            for (index in start until end) {
              val tn = numbers[index]
              statement.setLong(parameter++, tn.featureNumber)
              statement.setLong(parameter++, tn.version ushr 2)
            }
            statement.fetchSize = 500
            statement.executeQuery().use { rows ->
              while (rows.next()) {
                val tuple = ImlTuple.read(rows, storage.number, location.first, location.second, layer.collection.useMembers())
                val matches = targets[tuple.tupleNumber] ?: continue
                for (target in matches) {
                  ImlTuple.attach(target, tuple)
                  target.source = storage
                }
                if (useSharedCache) storage.cacheTuple(tuple, cacheGeneration)
              }
            }
          }
          start = end
        }
      }
    }
  }

  override fun isClosed(): Boolean = closed

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    storage.releaseSession(this)
  }
}
