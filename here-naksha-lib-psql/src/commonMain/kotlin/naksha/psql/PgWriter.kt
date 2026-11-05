@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.PlatformUtil
import naksha.base.collectionNotFound
import naksha.base.forbidden
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.catalogExists
import naksha.base.catalogNotFound
import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers
import naksha.model.request.*
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A helper to write tuples into collections.
 *
 * This class is stateful and should be sticky until `commit` or `rollback`. It will remember which maps and collections have been created within the current transaction. After `commit`, the corresponding new objects should be visible, and the cache should automatically fetch them, but until this point, they are only visible within the current transaction!
 * @since 3.0
 * @see [PgWrite]
 */
@JsExport
open class PgWriter internal constructor(
  /**
   * The session to which the writer is bound.
   * @since 3.0
   */
  session: PgSession,

  /**
   * Whether the writer should use savepoints.
   * @since 3.0
   */
  @JvmField
  val useSavepoint: Boolean
) : PgReaderWriterBase(session) {
  companion object PgWriterCompanion {
    /**
     * Resolve storage defaults before encoding, preserving ordered immutability for explicitly supplied layouts.
     * @since 3.0
     */
    internal fun prepareCollectionLayout(collection: NakshaCollection, existing: NakshaCollection? = null) {
      collection.validateLayoutDeclarations()
      if (existing != null && collection.members != null) validateCollectionSchemaUnchanged(existing, collection)
      PgCollection.resolveLayout(collection)
      if (existing != null) validateCollectionSchemaUnchanged(existing, collection)
    }

    private fun requireSameValues(left: List<*>?, right: List<*>?) {
      if (left == null || right == null) {
        if (left !== right) throw illegalArg("Collection members and indices are immutable after creation")
        return
      }
      val size = left.size
      if (size != right.size) throw illegalArg("Collection members and indices are immutable after creation")
      for (i in 0 until size) {
        if (left[i] != right[i]) throw illegalArg("Collection members and indices are immutable after creation")
      }
    }

    /**
     * Requires the same declared members and indices in the same order. Compares corresponding fields directly and stops at the first difference without copying, serializing or sorting declarations. An absent declaration differs from an explicitly empty one; storage-maintained member positions are ignored.
     * @since 3.0
     */
    internal fun validateCollectionSchemaUnchanged(existing: NakshaCollection, incoming: NakshaCollection) {
      val existingMembers = existing.members
      val incomingMembers = incoming.members
      if (existingMembers == null || incomingMembers == null) {
        if (existingMembers !== incomingMembers) throw illegalArg("Collection members are immutable after creation")
      } else {
        val size = existingMembers.size
        if (size != incomingMembers.size) throw illegalArg("Collection members are immutable after creation")
        for (i in 0 until size) {
          val previous = existingMembers[i] ?: throw illegalArg("Collection member must not be null")
          val next = incomingMembers[i] ?: throw illegalArg("Collection member must not be null")
          if (previous.name != next.name || previous.dataType.value != next.dataType.value ||
            previous.retain.value != next.retain.value || previous.conditional != next.conditional ||
            previous.isMandatory() != next.isMandatory() || previous.isVirtual() != next.isVirtual()) {
            throw illegalArg("Collection members are immutable after creation")
          }
          requireSameValues(previous.path, next.path)
        }
      }

      val existingIndices = existing.indices
      val incomingIndices = incoming.indices
      if (existingIndices == null || incomingIndices == null) {
        if (existingIndices !== incomingIndices) throw illegalArg("Collection indices are immutable after creation")
        return
      }
      val size = existingIndices.size
      if (size != incomingIndices.size) throw illegalArg("Collection indices are immutable after creation")
      for (i in 0 until size) {
        val previous = existingIndices[i] ?: throw illegalArg("Collection index must not be null")
        val next = incomingIndices[i] ?: throw illegalArg("Collection index must not be null")
        if (previous.name != next.name || previous.isConditional() != next.isConditional() ||
          (previous.getRaw("unique") == true) != (next.getRaw("unique") == true) ||
          (previous.getRaw("internal") == true) != (next.getRaw("internal") == true)) {
          throw illegalArg("Collection indices are immutable after creation")
        }
        requireSameValues(previous.on, next.on)
        requireSameValues(previous.order, next.order)
        requireSameValues(previous.include, next.include)
      }
    }
  }

  /**
   * The storage to operate on.
   * @since 3.0
   */
  val storage: PgStorage = session.storage

  /**
   * The Naksha transaction.
   * @since 3.0
   */
  val transaction: NakshaTx
    get() = session.useTransaction()

  /** Actual changed identities from the last successful request, including purges without response tuples. @since 3.0 */
  internal var mutatedFeatures: Set<Triple<Id, Id, Id>> = emptySet()
    private set

  /**
   * Performs the given writes.
   * @param writes the writes to perform.
   * @return the response.
   * @since 3.0
   */
  fun execute(writes: WriteList): Response {
    mutatedFeatures = emptySet()
    val requests = ArrayList<Write>(writes.size)
    for (write in writes) {
      if (write != null) requests.add(write)
    }
    val featureTupleList = executeWrites(requests)
    return SuccessResponse().withFeatureTupleList(featureTupleList)
  }

  /**
   * Performs the given writes.
   *
   * The returned [FeatureTupleList] carries the written tuples by hard reference (in input order), so the response keeps them reachable for as long as it is used, instead of relying on the soft-referenced cache retaining them.
   * @param writes the writes to perform.
   * @return the written feature-tuples, in input order (writes without a tuple-number are skipped).
   * @since 3.0
   */
  private fun executeWrites(writes: MutableList<Write>): FeatureTupleList {
    if (writes.isEmpty()) return FeatureTupleList()
    // Retain input indices while ordering writes for row locking.
    val size = writes.size
    val pgWrites = ArrayList<PgWrite>(size)
    val catalogDrops = ArrayList<PgWrite>()
    val collectionDrops = ArrayList<PgWrite>()
    val catalogGuards = HashSet<Id>()
    val collectionGuards = HashSet<Pair<Id, Id>>()
    val writeCollections = HashSet<Pair<Id, Id>>()
    val exclusiveCollections = HashSet<Pair<Id, Id>>()
    val catalogPreparations = HashSet<Id>()
    val exclusiveCatalogs = HashSet<Id>()
    for (i in 0 until size) {
      val write = PgWrite(writes[i], i)
      val original = write.original
      val catalogId = original.catalogId ?: throw illegalArg("The given write does not have a catalog-id")
      val collectionId = original.collectionId ?: throw illegalArg("The given write does not have a collection-id")
      val op = write.op
      if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
        if (write.isCatalogModification && write.id == Id.ADMIN_CATALOG_ID ||
          write.isCollectionModification && (write.id == Id.COLLECTIONS_COL_ID ||
            catalogId == Id.ADMIN_CATALOG_ID && (write.id == Id.CATALOGS_COL_ID ||
              write.id == Id.TRANSACTIONS_COL_ID || write.id == Id.BOOKS_COL_ID))) {
          throw forbidden("Built-in catalog and collections must not be deleted or purged")
        }
      }
      pgWrites.add(write)
      catalogGuards.add(catalogId)
      collectionGuards.add(catalogId to collectionId)
      writeCollections.add(catalogId to collectionId)
      if (op != WriteOp.DELETE && op != WriteOp.CREATE && op != WriteOp.UPSERT) continue
      if (write.isCatalogModification) {
        catalogGuards.add(write.id)
        if (op == WriteOp.DELETE) {
          catalogDrops.add(write)
          exclusiveCatalogs.add(write.id)
        } else {
          catalogPreparations.add(write.id)
          collectionGuards.add(write.id to Id.COLLECTIONS_COL_ID)
        }
      } else if (write.isCollectionModification) {
        catalogGuards.add(catalogId)
        collectionGuards.add(catalogId to write.id)
        exclusiveCollections.add(catalogId to write.id)
        if (op == WriteOp.DELETE) collectionDrops.add(write)
      }
    }
    catalogDrops.sortWith(compareBy { it.id })
    collectionDrops.sortWith(compareBy({ it.original.catalogId }, { it.id }))
    val orderedCatalogs = ArrayList(catalogGuards)
    orderedCatalogs.sort()
    val orderedCollections = ArrayList(collectionGuards)
    orderedCollections.sortWith(compareBy({ it.first }, { it.second }))
    val savepointId = PlatformUtil.randomString()
    session.useConn(false) { conn ->
      var savepointCreated = false
      try {
        // Allocate the logical version before the savepoint: the allocator completes its own transaction.
        session.useTransaction()
        if (useSavepoint) {
          conn.execute("SAVEPOINT \"$savepointId\"").close()
          savepointCreated = true
        }
        // Acquire the combined set in catalog order with scoped modes before preparation or row locks. Existing schema reuse stays shared; later upgrades use PgPartitions' nonblocking retry path.
        for (catalogId in orderedCatalogs) {
          val schema = catalogId.catalogName()
          val exclusive = catalogId in exclusiveCatalogs ||
            catalogId in catalogPreparations && storage.database.catalog_get_schema_oid(conn, schema) < 0
          PgPartitions.lockCatalog(conn, schema, shared = !exclusive)
        }
        // Probe the entire combined set before upgrading anything. Releasing this subtransaction's shared guards avoids two missing-coverage writers deadlocking on an upgrade.
        val coverageSavepoint = "coverage_$savepointId"
        conn.execute("SAVEPOINT \"$coverageSavepoint\"").close()
        val missing = HashSet<Pair<Id, Id>>()
        val newCollections = HashSet<Pair<Id, Id>>()
        val version = session.useTransaction().version.number
        val newCatalogs = HashSet<Id>()
        for (catalogId in catalogPreparations) {
          if (!catalogCollectionsExist(conn, catalogId)) newCatalogs.add(catalogId)
        }
        for ((catalogId, collectionId) in orderedCollections) {
          PgPartitions.lockCollection(conn, catalogId.catalogName(), collectionId.collectionName(), shared = true)
          if (catalogId in newCatalogs || catalogId to collectionId in exclusiveCollections &&
            !PgPartitions.exists(conn, catalogId.catalogName(), collectionId.collectionName())) {
            newCollections.add(catalogId to collectionId)
            continue
          }
          // Guard-only DDL targets may be tombstones with no physical root or registration. Their metadata rows live in the write destination, not in the dropped target.
          if (catalogId to collectionId !in writeCollections) continue
          val collection = session.getPgCollection(catalogId.intValue, collectionId.intValue, conn)
          if (collection == null) {
            newCollections.add(catalogId to collectionId)
          } else if (collection.storeHistory) {
            try {
              if (!PgPartitions.covered(conn, catalogId.catalogName(), collection.name, version)) missing.add(catalogId to collectionId)
            } catch (e: NakshaException) {
              if (e.error.code == NakshaError.COLLECTION_NOT_FOUND) {
                // Cache keys are logical identities, not physical aliases from the coverage probe.
                e.error.database = storage.id.text
                e.error.catalog = catalogId.text
                e.error.collection = collectionId.text
              }
              throw e
            }
          }
        }
        if (missing.isNotEmpty() || exclusiveCollections.isNotEmpty() || newCollections.isNotEmpty()) {
          conn.execute("ROLLBACK TO SAVEPOINT \"$coverageSavepoint\"").close()
          for ((catalogId, collectionId) in orderedCollections) {
            val key = catalogId to collectionId
            PgPartitions.lockCollection(conn, catalogId.catalogName(), collectionId.collectionName(),
              shared = key !in missing && key !in exclusiveCollections && key !in newCollections)
          }
          for ((catalogId, collectionId) in orderedCollections) {
            if (catalogId to collectionId in missing) {
              try {
                if (!PgPartitions.covered(conn, catalogId.catalogName(), collectionId.collectionName(), version)) {
                  storage.database.collection_maintain_partitions(conn, catalogId.catalogName(), collectionId.collectionName(), version)
                }
              } catch (e: NakshaException) {
                if (e.error.code == NakshaError.COLLECTION_NOT_FOUND) {
                  e.error.database = storage.id.text
                  e.error.catalog = catalogId.text
                  e.error.collection = collectionId.text
                }
                throw e
              }
            }
          }
        }
        conn.execute("RELEASE SAVEPOINT \"$coverageSavepoint\"").close()
        prepareWrite(conn, pgWrites)

        // Provision successor ranges before any row locks; small shifts/imports can exceed premade coverage.
        val preparedRanges = HashSet<String>()
        for (write in pgWrites) {
          if (!write.dbCollection.storeHistory || write.dbCollection.catalogId to write.dbCollection.id !in newCollections) continue
          val collection = write.dbCollection
          val range = "${collection.catalogId.intValue}:${collection.id.intValue}:${version shr collection.shift}"
          if (preparedRanges.add(range)) storage.database.collection_maintain_partitions(conn, collection.catalogId.catalogName(), collection.name, version)
        }

        // Lock rows by catalog, collection and ID, independent of operation or atomicity.
        pgWrites.sortWith(compareBy({ it.dbCatalog.id }, { it.dbCollection.id }, { it.id }))

        // Perform the writes, if any error happens, we will roll back the session to where it was before we started.
        // Note: We must not close the connection, therefore no `session.useConnection().use {}`!
        var start = 0
        for (i in 1..size) {
          if (i == size ||
            pgWrites[i].dbCatalog.id != pgWrites[start].dbCatalog.id ||
            pgWrites[i].dbCollection.id != pgWrites[start].dbCollection.id
          ) {
            executeWrite(conn, pgWrites, start, i)
            start = i
          }
        }
        // Drop physical data only after every logical mutation has passed validation.
        for (write in collectionDrops) storage.database.collection_drop_if_exists(conn, write.dbCatalog.name, write.id.collectionName())
        for (write in catalogDrops) storage.database.catalog_drop_schema(conn, write.id.catalogName())
        // We can drop the savepoint, if there is any.
        if (savepointCreated) conn.execute("RELEASE SAVEPOINT \"$savepointId\"").close()
      } catch (t: Throwable) {
        if (savepointCreated) {
          conn.execute("ROLLBACK TO SAVEPOINT \"$savepointId\"").close()
        } else {
          // The first request has no earlier writes to preserve and therefore no savepoint.
          conn.rollback()
        }
        throw PgExceptionMapper.map(t)
      }
    }

    // Invalidate cached catalogs/collections that were modified, so subsequent reads re-fetch their
    // current state from the database instead of returning a stale cached entry (e.g. a pre-deletion
    // entry after a DELETE, or a lingering tombstone after a re-creating CREATE).
    for (pgWrite in pgWrites) {
      if (pgWrite.isCatalogModification) {
        session.invalidatePgCatalog(pgWrite.id.intValue)
      } else if (pgWrite.isCollectionModification) {
        session.invalidatePgCollection(pgWrite.dbCatalog.id.intValue, pgWrite.id.intValue)
      }
    }

    // Reorder results to match input.
    val tupleList = ArrayList<Tuple>(size)
    val transaction = this.transaction
    var featuresModified = 0
    for (pgWrite in pgWrites) {
      val tupleNumber = pgWrite.tupleNumber
      val tuple = pgWrite.tuple
      if (pgWrite.isCatalogModification) {
        transaction.useCatalog(pgWrite.id, pgWrite.action)
      } else if (pgWrite.isCollectionModification) {
        transaction.useCatalog(pgWrite.dbCatalog.id).useCollection(pgWrite.id, pgWrite.action)
      } else {
        val catalog = pgWrite.dbCatalog
        val collection = pgWrite.dbCollection
        val txCol = transaction.useCatalog(catalog.id).useCollection(collection.id)
        if (tupleNumber != null) {
          txCol.add(tupleNumber, collection.partitions)
        }
        featuresModified += 1
      }
      if (tuple != null) tupleList.add(tuple)
    }
    transaction.featuresModified += featuresModified
    // We do not put tuples into cache, before we are sure everything was successful!
    // Adding all together into the cache reduces the effort to iterate above all caches multiple times.
    Naksha.cache.store(tupleList)

    val featureTuples = FeatureTupleList()
    val changed = HashSet<Triple<Id, Id, Id>>()
    featureTuples.setCapacity(size)
    for (pgWrite in pgWrites) {
      if (pgWrite.changed || pgWrite.op != WriteOp.DELETE && pgWrite.op != WriteOp.PURGE) {
        changed.add(Triple(pgWrite.dbCatalog.id, pgWrite.dbCollection.id, pgWrite.id))
      }
      val tupleNumber = pgWrite.tupleNumber
      if (tupleNumber != null) {
        val tuple = pgWrite.tuple
        featureTuples[pgWrite.i] = if (tuple != null) FeatureTuple(tupleNumber, tuple) else FeatureTuple(tupleNumber)
      }
    }
    mutatedFeatures = changed
    return featureTuples
  }

  /**
   * Prepares catalog/collection metadata and encoded states, delegating schema and table creation to [PgDatabase]. Ensures [PgWrite] has the properties needed for execution, or throws [naksha.base.NakshaException].
   * @param conn the connection to use to read/write.
   * @param pgWrites the writes to process.
   * @since 3.0
   */
  private fun prepareWrite(conn: PgConnection, pgWrites: ArrayList<PgWrite>) {
    val database = storage.database
    val databaseId = database.id.text
    for (pgWrite in pgWrites) {
      // Detect the catalog into which to write (can be `naksha~admin`).
      val original = pgWrite.original
      val catalogId: Id = original.catalogId ?: throw illegalArg("The given write does not have a catalog-id")
      val catalogNumber = catalogId.intValue
      val dbCatalog: PgCatalog = session.getPgCatalog(catalogNumber, conn)
        ?: throw catalogNotFound("The write #${pgWrite.i} refers to not existing catalog '$catalogId'")
      pgWrite.dbCatalog = dbCatalog

      // Detect the collection into which to write.
      val collectionId: Id = original.collectionId ?: throw illegalArg("The given write does not have a collection-id")
      val collectionNumber = collectionId.intValue
      val dbCollection: PgCollection = session.getPgCollection(catalogNumber, collectionNumber, conn)
        ?: throw collectionNotFound("The write #${pgWrite.i} refers to not existing collection '$collectionId'")
      pgWrite.dbCollection = dbCollection

      // Ensure the feature is of the correct type.
      val id = pgWrite.id
      val op = pgWrite.op
      // Atomic UPDATE, UPSERT and DELETE require an expected version, including metadata writes.
      if (pgWrite.atomic && pgWrite.requireVersion == null &&
        (op == WriteOp.UPDATE || op == WriteOp.UPSERT || op == WriteOp.DELETE)) {
        if (op == WriteOp.UPDATE && pgWrite.isCollectionModification && session.getPgCollection(catalogNumber, id.intValue, conn) == null) {
          throw collectionNotFound("Collection '${id.text}' does not exist in catalog '${catalogId.text}'")
        }
        if (op == WriteOp.UPDATE && pgWrite.isCatalogModification && session.getPgCatalog(id.intValue, conn) == null) {
          throw catalogNotFound("Catalog '${id.text}' does not exist")
        }
        throw illegalArg("Atomic $op of feature '${id.text}' requires a version (uuid) to check against")
      }
      val feature = pgWrite.feature ?: original.feature
      pgWrite.feature = feature
      if (feature != null) {
        if (pgWrite.isCatalogModification) {
          val catalog = feature as? NakshaCatalog ?: feature.proxy(NakshaCatalog::class)
          catalog.databaseId = databaseId
          pgWrite.feature = catalog
        } else if (pgWrite.isCollectionModification) {
          val collection = feature as? NakshaCollection ?: feature.proxy(NakshaCollection::class)
          if (op == WriteOp.CREATE || op == WriteOp.UPDATE || op == WriteOp.UPSERT) {
            val existing = if (op == WriteOp.UPDATE || op == WriteOp.UPSERT) session.getPgCollection(catalogNumber, id.intValue, conn) else null
            prepareCollectionLayout(collection, existing?.naksha)
          }
          collection.databaseId = databaseId
          val partitions = collection.partitions
          if (partitions !in 0..256) {
            throw illegalArg("Invalid partition-count, expect 2 .. 256, found : $partitions")
          }
          pgWrite.feature = collection
        } else if (pgWrite.isTransactionModification) {
          val transaction = feature as? NakshaTx ?: feature.proxy(NakshaTx::class)
          pgWrite.feature = transaction
        }
      }

      // Catalog and collection metadata follow the normal feature modification steps.
      when (op) {
        WriteOp.CREATE -> {
          val f: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
          val tuple = Tuple.encodeFeature(f, dbCollection.naksha, session, action = Action.CREATE).tuple
          pgWrite.tuple = tuple
          pgWrite.tupleNumber = tuple.tupleNumber
          if (f is NakshaCatalog) {
            val pgCatalog = PgCatalog(tuple)
            if (database.catalog_get_or_create_schema(conn, name = pgCatalog.name, originalName = pgCatalog.id.text) < 0) {
              throw catalogExists("Creation of catalog '$id' failed, it exists already")
            }
            val collections = prepareCatalogCollections(conn, pgCatalog)
            pgWrite.pgCatalog = pgCatalog
            session.cachePgCatalog(pgCatalog) // This allows following write instructions to succeed.
            session.cachePgCollection(collections)
          } else if (f is NakshaCollection) {
            val pgCollection = PgCollection(catalogId, tuple)
            database.collection_create_table_if_not_exists(
              conn = conn,
              schema = dbCatalog.name,
              table = pgCollection.name,
              columns = pgCollection.columns,
              indices = pgCollection.indices,
              options = pgCollection.creationOptions(storage.config, database.maxTupleSize),
              logicalCatalog = catalogId.text,
              logicalCollection = pgCollection.id.text,
            )
            pgWrite.pgCollection = pgCollection
            session.cachePgCollection(pgCollection) // This allows following write instructions to succeed.
          }
        }
        WriteOp.UPDATE -> {
          val f: NakshaFeature = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
          val tuple = Tuple.encodeFeature(f, dbCollection.naksha, session, action = Action.UPDATE, atomic = pgWrite.requireVersion != null).tuple
          pgWrite.tuple = tuple
          pgWrite.tupleNumber = tuple.tupleNumber
          // No special database operations needed upfront.
        }
        WriteOp.UPSERT -> {
          // Encode CREATE first; the SQL chooses UPDATE when insertion conflicts.
          // Preserve the legacy UUID-removal behavior until the UPSERT migration.
          val tnMember = dbCollection.naksha.validatedMembers.useMember(StandardMembers.Tn)
          val source = pgWrite.feature ?: throw illegalArg("The feature #${pgWrite.i} is null")
          val f = Tuple.shallowCopy(source, arrayOf(tnMember.path))
          tnMember.delete(f)
          val tuple = Tuple.encodeFeature(f, dbCollection.naksha, session, action = Action.CREATE).tuple
          pgWrite.tuple = tuple
          pgWrite.tupleNumber = tuple.tupleNumber
          if (f is NakshaCatalog) {
            val pgCatalog = PgCatalog(tuple)
            database.catalog_get_or_create_schema(conn, name = pgCatalog.name, originalName = pgCatalog.id.text)
            val collections = prepareCatalogCollections(conn, pgCatalog)
            pgWrite.pgCatalog = pgCatalog
            session.cachePgCatalog(pgCatalog) // This allows following write instructions to succeed.
            session.cachePgCollection(collections)
          } else if (f is NakshaCollection) {
            val pgCollection = PgCollection(catalogId, tuple)
            database.collection_create_table_if_not_exists(
              conn = conn,
              schema = dbCatalog.name,
              table = pgCollection.name,
              columns = pgCollection.columns,
              indices = pgCollection.indices,
              options = pgCollection.creationOptions(storage.config, database.maxTupleSize),
              logicalCatalog = catalogId.text,
              logicalCollection = pgCollection.id.text,
            )
            pgWrite.pgCollection = pgCollection
            session.cachePgCollection(pgCollection) // This allows following write instructions to succeed.
          }
        }
        // TODO: Fix that DELETE uses feature and can produce a valid new state!
        // TODO: Fix that we can make empty tombstones, so new tombstone rows with no data!
        WriteOp.DELETE, WriteOp.PURGE -> {
          // Deletes carry no tuple-number; PgWriterDelete resolves the existing HEAD by feature-number.
          if (pgWrite.isTransactionModification) throw forbidden("Transactions must not be deleted or purged")
        }
        else -> throw illegalArg("Unknown write operation: $op")
      }
    }
  }

  /**
   * Ensures an ordinary catalog's internal collections table exists before publishing its descriptor to the session cache.
   * @since 3.0
   */
  private fun prepareCatalogCollections(conn: PgConnection, catalog: PgCatalog): PgCollection {
    val database = storage.database
    if (catalog.id == Id.ADMIN_CATALOG_ID) return database.pgAdminCollections
    val collection = catalog.collectionsDescriptor(database.pgAdminCollections.naksha)
    if (catalogCollectionsExist(conn, catalog.id)) return collection
    database.collection_create_table_if_not_exists(
      conn = conn,
      schema = catalog.name,
      table = collection.name,
      columns = collection.columns,
      indices = collection.indices,
      options = collection.creationOptions(storage.config, database.maxTupleSize),
      logicalCatalog = catalog.id.text,
      logicalCollection = collection.id.text,
    )
    return collection
  }

  /**
   * Checks physical ownership under the shared catalog/collection guards without reserving an existing metadata table exclusively. A missing table is created only after the combined guard pass reserves it exclusively.
   * @since 3.0
   */
  private fun catalogCollectionsExist(conn: PgConnection, catalogId: Id): Boolean {
    if (catalogId == Id.ADMIN_CATALOG_ID) return true
    val table = Id.COLLECTIONS_COL_ID.collectionName()
    conn.execute("""
SELECT pg_catalog.obj_description(c.oid, 'pg_class') AS owner
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = $1 AND c.relname = $2
""", arrayOf(catalogId.catalogName(), table)).use { cursor ->
      if (!cursor.next()) return false
      val owner = "${PgUtil.quoteIdent(catalogId.text)}.${PgUtil.quoteIdent(Id.COLLECTIONS_COL_ID.text)}"
      if (cursor.column("owner") != owner) throw illegalState("Physical catalog collections table belongs to another logical identity")
      return true
    }
  }

  /**
   * Execute all writes between `start` _(inclusive)_ and `end` _(exclusive)_.
   * @param conn the connection to use for modifications.
   * @param pgWrites the list of ordered writes.
   * @param start the index in the list of the first element to execute.
   * @param end the index in the list of the first element **NOT** to execute _(excluded)_.
   * @since 3.0
   */
  private fun executeWrite(conn: PgConnection, pgWrites: ArrayList<PgWrite>, start: Int, end: Int) {
    if (start == end) return
    // All writes in [start, end) share one catalog and collection, ordered by ID.
    val pgCollection = pgWrites[start].dbCollection
    var s = start
    while (s < end) {
      val op = pgWrites[s].op
      var e = s + 1
      while (e < end && pgWrites[e].op == op) e++
      when (op) {
        WriteOp.DELETE -> PgWriterDelete(this, pgCollection, pgWrites, s, e).execute(conn)
        WriteOp.PURGE -> PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = true).execute(conn)
        WriteOp.CREATE -> PgWriterInsert(this, pgCollection, pgWrites, s, e).execute(conn)
        WriteOp.UPSERT -> PgWriterUpsert(this, pgCollection, pgWrites, s, e).execute(conn)
        WriteOp.UPDATE -> PgWriterUpdate(this, pgCollection, pgWrites, s, e).execute(conn)
        else -> throw illegalArg("Unknown write operation: $op")
      }
      s = e
    }
  }

}
