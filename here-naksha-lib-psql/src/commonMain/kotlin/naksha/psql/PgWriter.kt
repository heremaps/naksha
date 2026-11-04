@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Action
import naksha.base.Id
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

  /**
   * Performs the given writes.
   * @param writes the writes to perform.
   * @return the response.
   * @since 3.0
   */
  fun execute(writes: WriteList): Response {
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
    // Add the input-index as preparation for sorting by db, catalog, collection, partition.
    val size = writes.size
    val pgWrites = ArrayList<PgWrite>(size)
    for (i in 0 until size) {
      pgWrites.add(PgWrite(writes[i], i))
    }
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
        prepareWrite(conn, pgWrites)

        // Provision successor ranges before any row locks; small shifts/imports can exceed premade coverage.
        val preparedRanges = HashSet<String>()
        for (write in pgWrites) {
          if (write.op != WriteOp.CREATE || !write.dbCollection.storeHistory) continue
          val collection = write.dbCollection
          val tuple = write.tuple ?: throw illegalState("CREATE requires an encoded tuple")
          val range = "${collection.catalogId.intValue}:${collection.id.intValue}:${tuple.version shr collection.shift}"
          if (preparedRanges.add(range)) storage.database.collection_maintain_partitions(conn, collection.catalogId.catalogName(), collection.name, tuple.version)
        }

        // The ordering is very important, because otherwise there can be deadlocks in the database at row-level locking!
        pgWrites.sortWith { a, b -> a.id.compareTo(b.id) }

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
        // We can drop the savepoint, if there is any.
        if (savepointCreated) conn.execute("RELEASE SAVEPOINT \"$savepointId\"").close()
      } catch (t: Throwable) {
        if (savepointCreated) {
          conn.execute("ROLLBACK TO SAVEPOINT \"$savepointId\"").close()
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
    featureTuples.setCapacity(size)
    for (pgWrite in pgWrites) {
      val tupleNumber = pgWrite.tupleNumber
      if (tupleNumber != null) {
        val tuple = pgWrite.tuple
        featureTuples[pgWrite.i] = if (tuple != null) FeatureTuple(tupleNumber, tuple) else FeatureTuple(tupleNumber)
      }
    }
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
      // An atomic feature update/upsert must carry a version to check against; without one the
      // caller's intent cannot be honored, so reject it instead of silently degrading to non-atomic.
      if (pgWrite.isFeatureModification && pgWrite.atomic && pgWrite.requireVersion == null &&
        (op == WriteOp.UPDATE || op == WriteOp.UPSERT)) {
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
   * Execute all writes between `start` _(inclusive)_ and `end` _(exclusive)_.
   * @param conn the connection to use for modifications.
   * @param pgWrites the list of ordered writes.
   * @param start the index in the list of the first element to execute.
   * @param end the index in the list of the first element **NOT** to execute _(excluded)_.
   * @since 3.0
   */
  private fun executeWrite(conn: PgConnection, pgWrites: ArrayList<PgWrite>, start: Int, end: Int) {
    if (start == end) return
    // All writes in [start, end) share one catalog and collection, ordered by operation.
    val pgCollection = pgWrites[start].dbCollection
    var s = start
    var e = start

    // DELETE
    while (e < end && pgWrites[e].op == WriteOp.DELETE) e++
    if (e > s) {
      PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = false).execute(conn)
      s = e
    }

    // PURGE
    while (e < end && pgWrites[e].op == WriteOp.PURGE) e++
    if (e > s) {
      PgWriterDelete(this, pgCollection, pgWrites, s, e, purge = true).execute(conn)
      s = e
    }

    // CREATE
    while (e < end && pgWrites[e].op == WriteOp.CREATE) e++
    if (e > s) {
      PgWriterInsert(this, pgCollection, pgWrites, s, e).execute(conn)
      s = e
    }

    // UPSERT
    while (e < end && pgWrites[e].op == WriteOp.UPSERT) e++
    if (e > s) {
      PgWriterUpsert(this, pgCollection, pgWrites, s, e).execute(conn)
      s = e
    }

    // UPDATE
    while (e < end && pgWrites[e].op == WriteOp.UPDATE) e++
    if (e > s) {
      PgWriterUpdate(this, pgCollection, pgWrites, s, e).execute(conn)
      s = e
    }

    if (e != end) throw illegalState("We missed some writes beyond $s in the ordered write-operation list")
  }

}
