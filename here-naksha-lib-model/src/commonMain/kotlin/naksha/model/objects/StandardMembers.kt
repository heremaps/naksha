@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The canonical set of standard members that every Naksha storage understands.
 *
 * Members are divided into three groups:
 *
 * ### Mandatory members
 * These are always managed and persisted by the storage, regardless of whether they appear in
 * [NakshaCollection.members]. Clients must not declare them with a different [MemberType].
 * Their [Member.path] is always `null` — the storage resolves their values internally.
 *
 * - [StandardMembers_C.DatabaseNumber] — database-number (storage-level identifier)
 * - [StandardMembers_C.CatalogNumber] — catalog-number (map-level identifier)
 * - [StandardMembers_C.CollectionNumber] — collection-number (collection-level identifier)
 * - [StandardMembers_C.FeatureNumber]  — feature-number (per-collection feature identifier, routing key)
 * - [StandardMembers_C.Version] — version of the tuple (with action bits in the lower 2 bits)
 * - [StandardMembers_C.NextVersion] — version at which this state was superseded (history only)
 * - [StandardMembers_C.Feature] — the serialised feature blob
 * - [StandardMembers_C.GlobalBookNumber] — global-book-number for JBON2 global-book decoding (sequencer-assigned)
 *
 * ### Special members
 * These members are **not** part of the default schema. They must be explicitly declared in
 * [NakshaCollection.members] to be materialised. They are defined here so that all storage
 * implementations share a consistent name and type contract.
 *
 * - [StandardMembers_C.PublishNumber] — publisher-assigned sequential visibility number (`pn`, INT64)
 * - [StandardMembers_C.PublishTime] — epoch-millis timestamp when the publisher sequenced the transaction (`pt`, INT64)
 * - [StandardMembers_C.GlobalVersion] — HERE global version number (`gv`, INT64)
 *
 * ### Default members
 * These are included automatically when [NakshaCollection.members] is `null` (backward-compatible
 * full schema). When [NakshaCollection.members] is explicitly set (even to an empty list), only
 * the mandatory members plus the explicitly declared members are materialised.
 *
 * - [StandardMembers_C.UpdatedAt], [StandardMembers_C.CreatedAt], [StandardMembers_C.AuthorTimestamp] — timestamps
 * - [StandardMembers_C.CustomValue0], [StandardMembers_C.CustomValue1], [StandardMembers_C.CustomValue2], [StandardMembers_C.CustomValue3] — custom numeric values
 * - [StandardMembers_C.Hash] — content hash
 * - [StandardMembers_C.HereTile] — HERE tile key of the reference point
 * - [StandardMembers_C.ChangeCount] — change-count
 * - [StandardMembers_C.BaseTupleNumber] — base tuple-number (three-way merge support)
 * - [StandardMembers_C.Id] — feature string identifier (only for named features where `fn < 0`)
 * - [StandardMembers_C.AppId] — application identifier
 * - [StandardMembers_C.Author] — author identifier
 * - [StandardMembers_C.Origin] — origin reference (forking / rebase support)
 * - [StandardMembers_C.Target] — join target reference
 * - [StandardMembers_C.FeatureType] — feature-type (only stored when it differs from the collection default)
 * - [StandardMembers_C.CustomString0], [StandardMembers_C.CustomString1], [StandardMembers_C.CustomString2], [StandardMembers_C.CustomString3] — custom string values
 * - [StandardMembers_C.Tags] — feature tags (set of unique strings, order preserved)
 * - [StandardMembers_C.ReferencePoint] — geometry reference point (TWKB, [MemberType.SPATIAL])
 * - [StandardMembers_C.Geometry] — feature geometry (TWKB, [MemberType.SPATIAL])
 * - [StandardMembers_C.Attachment] — arbitrary binary attachment
 *
 * @since 3.0
 */
@JsExport
class StandardMembers private constructor() {

    companion object StandardMembers_C {

        // -------------------------------------------------------------------------
        // Mandatory members — storage-managed, always present, no JSON path
        // -------------------------------------------------------------------------

        /**
         * `dbn` — **Database-number** (`INT64`). The storage-level numeric identifier of the storage
         * instance. Mandatory, storage-managed. Not materialised as a physical column in all
         * implementations (e.g. `lib-psql` treats it as implicit context).
         * @since 3.0
         */
        @JvmField @JsStatic
        val DatabaseNumber = Member("dbn", MemberType.INT64)

        /**
         * `catn` — **Catalog-number** (`INT32`). The map-level numeric identifier. Mandatory,
         * storage-managed. Not materialised as a physical column in all implementations
         * (e.g. `lib-psql` treats it as implicit schema context).
         * @since 3.0
         */
        @JvmField @JsStatic
        val CatalogNumber = Member("catn", MemberType.INT32)

        /**
         * `coln` — **Collection-number** (`INT32`). The collection-level numeric identifier. Mandatory,
         * storage-managed. Not materialised as a physical column in all implementations
         * (e.g. `lib-psql` treats it as implicit context).
         * @since 3.0
         */
        @JvmField @JsStatic
        val CollectionNumber = Member("coln", MemberType.INT32)

        /**
         * `fn` — **Feature-number** (`INT64`). The per-collection identifier of the feature. Together
         * with [Version] this uniquely identifies a tuple. The lower 16 bits are used as the partition
         * routing key. Mandatory, storage-managed.
         * @since 3.0
         */
        @JvmField @JsStatic
        val FeatureNumber = Member("fn", MemberType.INT64)

        /**
         * `version` — **Version** (`INT64`). The transaction number of the tuple, with the action
         * encoded in the lower 2 bits. Together with [FeatureNumber] this uniquely identifies a tuple.
         * Mandatory, storage-managed.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Version = Member("version", MemberType.INT64)

        /**
         * `next_version` — **Next-version** (`INT64`). The version at which this tuple was superseded
         * by the next state. Present only in history; in head the value is intrinsically the current
         * HEAD sentinel and is not stored as a physical member. Mandatory, storage-managed.
         * @since 3.0
         */
        @JvmField @JsStatic
        val NextVersion = Member("next_version", MemberType.INT64)

        /**
         * `feature` — **Serialised feature** (`BYTE_ARRAY`). The encoded feature blob. The encoding
         * is controlled by [NakshaCollection.dataEncoding]. Mandatory, storage-managed.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Feature = Member("feature", MemberType.BYTE_ARRAY)

        /**
         * `id` — feature string identifier. Either stringified [FeatureNumber] _(as positive decimal number, i.e. `12345678`)_ or the unique identifier of the feature, resulting in a negative [FeatureNumber], generated by hashing the `id`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Id = Member("id", MemberType.STRING)

        /**
         * `gbn` — **Global-book-number** (`INT64`). References the `global_book_fn` in
         * `naksha~books`, which carries the JBON2 global dictionary needed to decode this tuple's
         * [Feature] blob when global-book encoding is used. Assigned by the sequencer; `null` when
         * no global book is in use. Mandatory, storage-managed (but physically `null` until the
         * sequencer populates it).
         *
         * A conditional non-unique index on this member is always created so that the sequencer can
         * efficiently locate all tuples that reference a particular book.
         * @since 3.0
         */
        @JvmField @JsStatic
        val GlobalBookNumber = Member("gbn", MemberType.INT64)

        /**
         * All mandatory members, in declaration order.
         *
         * These are always managed and persisted by the storage regardless of what is declared in
         * [NakshaCollection.members]. No implementation-specific member may be declared with the
         * same name and a different [MemberType].
         * @since 3.0
         */
        @JvmField @JsStatic
        val MANDATORY: List<Member> = listOf(
            DatabaseNumber, CatalogNumber, CollectionNumber, FeatureNumber,
            Version, NextVersion, Feature, Id, GlobalBookNumber
        )

        // -------------------------------------------------------------------------
        // Special members — not in MANDATORY or DEFAULT; declared explicitly per collection
        // -------------------------------------------------------------------------

        /**
         * `pn` — **Publish-number** (`INT64`). A sequencer-assigned, gap-free sequential number
         * ordered by **visibility** rather than execution order. Unlike [Version], which can have
         * holes (rolled-back transactions) and allows concurrent transactions to commit out of
         * order (e.g. version 3 commits before version 2), the publish-number is a strict
         * monotonically increasing sequence with no gaps: after `5` comes `6`, guaranteed.
         *
         * This is important for change-event subscribers that need a reliable, ordered cursor
         * into the transaction stream. The publish-number is `null` until the publisher
         * (an external sequencer component) has processed the transaction.
         *
         * Currently used only in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishNumber = Member("pn", MemberType.INT64)

        /**
         * `pt` — **Publish-time** (`INT64`). The millisecond epoch timestamp at which the
         * publisher recognised and sequenced the transaction, assigning it its [PublishNumber].
         * `null` until the publisher has processed the transaction.
         *
         * Currently used only in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val PublishTime = Member("pt", MemberType.INT64)

        /**
         * `gv` — **Global-version** (`INT64`). The HERE-internal global version number,
         * assigned by HERE's global sequencing infrastructure. `null` until the global
         * sequencer has processed the transaction.
         *
         * Currently used only in `naksha~transactions`.
         * @since 3.0
         */
        @JvmField @JsStatic
        val GlobalVersion = Member("gv", MemberType.INT64)

        // -------------------------------------------------------------------------
        // Default members — included when NakshaCollection.members is null
        // -------------------------------------------------------------------------

        /**
         * `updated_at` — millisecond epoch timestamp of the last modification. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val UpdatedAt = Member("updated_at", MemberType.INT64, JsonPath("properties", "@ns:com:here:xyz", "updatedAt"))

        /**
         * `created_at` — millisecond epoch timestamp of the initial creation. `null` means the
         * timestamp equals [UpdatedAt] (first-write optimisation). Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CreatedAt = Member("created_at", MemberType.INT64, JsonPath("properties", "@ns:com:here:xyz", "createdAt"))

        /**
         * `author_ts` — millisecond epoch timestamp of the last author change. `null` means the
         * timestamp equals [UpdatedAt]. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val AuthorTimestamp = Member("author_ts", MemberType.INT64, JsonPath("properties", "@ns:com:here:xyz", "authorTs"))

        /**
         * `cv0` — custom numeric value 0 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue0 = Member("cv0", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv0"))

        /**
         * `cv1` — custom numeric value 1 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue1 = Member("cv1", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv1"))

        /**
         * `cv2` — custom numeric value 2 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue2 = Member("cv2", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv2"))

        /**
         * `cv3` — custom numeric value 3 (`FLOAT64`). `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomValue3 = Member("cv3", MemberType.FLOAT64, JsonPath("properties", "@ns:com:here:xyz", "cv3"))

        /**
         * `hash` — content hash of the tuple, computed by the storage. `null` if not recorded.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Hash = Member("hash", MemberType.INT32, JsonPath("properties", "@ns:com:here:xyz", "hash"))

        /**
         * `here_tile` — HERE tile key (binary) of the reference point. `null` if not known.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val HereTile = Member("here_tile", MemberType.INT32, JsonPath("properties", "@ns:com:here:xyz", "hereTile"))

        /**
         * `cc` — change-count: how many times this feature has been modified. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ChangeCount = Member("cc", MemberType.INT32, JsonPath("properties", "@ns:com:here:xyz", "changeCount"))

        /**
         * `base_tn` — base tuple-number (`BYTE_ARRAY`), set when a three-way merge was performed.
         * `null` otherwise. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val BaseTupleNumber = Member("base_tn", MemberType.BYTE_ARRAY, JsonPath("properties", "@ns:com:here:xyz", "base"))

        /**
         * `app_id` — identifier of the application that wrote this tuple. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val AppId = Member("app_id", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "appId"))

        /**
         * `author` — identifier of the human author that takes ownership for this tuple. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Author = Member("author", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "author"))

        /**
         * `origin` — stringified reference to the originating feature when this feature was forked or
         * copied from another storage, map, or collection. Used for rebase support. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Origin = Member("origin", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "origin"))

        /**
         * `target` — stringified reference to the feature into which this feature was joined.
         * Set when multiple features are merged into one. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Target = Member("target", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "target"))

        /**
         * `ft` — feature-type string. `null` when it matches the collection's
         * [default feature type][NakshaCollection.defaultFeatureType], avoiding redundant storage.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val FeatureType = Member("ft", MemberType.STRING, JsonPath("properties", "featureType"))

        /**
         * `cs0` — custom string value 0. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString0 = Member("cs0", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs0"))

        /**
         * `cs1` — custom string value 1. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString1 = Member("cs1", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs1"))

        /**
         * `cs2` — custom string value 2. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString2 = Member("cs2", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs2"))

        /**
         * `cs3` — custom string value 3. `null` if not used. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val CustomString3 = Member("cs3", MemberType.STRING, JsonPath("properties", "@ns:com:here:xyz", "cs3"))

        /**
         * `tags` — feature tags, the classic XYZ tags array located at
         * `properties -> @ns:com:here:xyz -> tags` (e.g. `["foo", "bar"]`), stored as a
         * [set][MemberType.SET] of unique strings. The array is persisted unmodified, so the
         * element order is preserved when reading the feature back. `null` if the feature has no
         * tags. Supports element containment queries via [IndexType.SET]. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Tags = Member("tags", MemberType.SET, JsonPath("properties", "@ns:com:here:xyz", "tags"))

        /**
         * `ref_point` — geometry reference point (always a single point), stored as TWKB. Used to
         * compute the [HereTile] value. `null` if the feature has no explicit reference point.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ReferencePoint = Member("ref_point", MemberType.SPATIAL, JsonPath("referencePoint"))

        /**
         * `geo` — feature geometry stored as TWKB. `null` if the feature has no geometry.
         * Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Geometry = Member("geo", MemberType.SPATIAL, JsonPath("geometry"))

        /**
         * `attachment` — arbitrary binary attachment. `null` if unused. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val Attachment = Member("attachment", MemberType.BYTE_ARRAY, JsonPath("attachment"))

        /**
         * `data_encoding` — the encoding used for the serialised feature blob (e.g. `JBON2`, `JSON_GZIP`).
         * `null` if not specified, in which case the storage default applies. Default member.
         * @since 3.0
         */
        @JvmField @JsStatic
        val DataEncoding = Member("data_encoding", MemberType.STRING)

        /**
         * The names of all [MANDATORY] members, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val MANDATORY_NAMES: Set<String> = MANDATORY.map { it.name }.toHashSet()

        /**
         * All default members (included when [NakshaCollection.members] is `null`), in declaration order.
         *
         * Does **not** include the [MANDATORY] members — those are always present regardless.
         * @since 3.0
         */
        @JvmField @JsStatic
        val DEFAULT: List<Member> = listOf(
            UpdatedAt, CreatedAt, AuthorTimestamp,
            CustomValue0, CustomValue1, CustomValue2, CustomValue3,
            Hash, HereTile, ChangeCount,
            BaseTupleNumber,
            AppId, Author, Origin, Target, FeatureType,
            CustomString0, CustomString1, CustomString2, CustomString3,
            Tags, ReferencePoint, Geometry, Attachment, DataEncoding
        )

        /**
         * The names of all [DEFAULT] members, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val DEFAULT_NAMES: Set<String> = DEFAULT.map { it.name }.toHashSet()

        /**
         * All special members — not added automatically but recognised by all storage implementations.
         * @since 3.0
         */
        @JvmField @JsStatic
        val SPECIAL: List<Member> = listOf(PublishNumber, PublishTime, GlobalVersion)

        /**
         * The names of all [SPECIAL] members, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val SPECIAL_NAMES: Set<String> = SPECIAL.map { it.name }.toHashSet()

        /**
         * All standard members: [MANDATORY] followed by [DEFAULT] followed by [SPECIAL].
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL: List<Member> = MANDATORY + DEFAULT + SPECIAL

        /**
         * The names of all standard members, for fast lookup.
         * @since 3.0
         */
        @JvmField @JsStatic
        val ALL_NAMES: Set<String> = ALL.map { it.name }.toHashSet()
    }
}
