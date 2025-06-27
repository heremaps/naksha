package naksha.model

import naksha.base.PlatformEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.model.FlagsBits.FlagsBits_C.OP_SHIFT
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * An enumeration about the operation being performed.
 *
 * This describes in greater detail what was logically done to feature, not what the effect was, which is described by the [Action].
 * @since 3.0
 * @see [Action]
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Operation : PlatformEnum() {
    override fun namespace() = TYPE
    override fun initClass() {}

    @Suppress("MemberVisibilityCanBePrivate")
    companion object Operation_C {
        /**
         * The [PlatformType] of [Operation].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(Operation::class).withPackageName(PACKAGE_NAME)

        // action = CREATE
        internal const val CREATE_VALUE = 0 shl OP_SHIFT
        internal const val CREATE_STRING = "CREATE"
        internal const val CREATED_STRING = "CREATED"
        internal const val CREATE_SHORT = "c"

        // action = UPDATE
        internal const val UPDATE_VALUE = 1 shl OP_SHIFT
        internal const val UPDATE_STRING = "UPDATE"
        internal const val UPDATED_STRING = "UPDATED"
        internal const val UPDATE_SHORT = "u"

        // action = DELETE
        internal const val DELETE_VALUE = 2 shl OP_SHIFT
        internal const val DELETE_STRING = "DELETE"
        internal const val DELETED_STRING = "DELETED"
        internal const val DELETE_SHORT = "d"

        // action = DELETE | UPDATE | CREATE
        internal const val REBASE_VALUE = 3 shl OP_SHIFT
        internal const val REBASE_STRING = "REBASE"
        internal const val REBASED_STRING = "REBASED"
        internal const val REBASE_SHORT = "b"

        // ---------------------------< extending action >----------------------------------------

        // action = CREATE
        internal const val FORK_VALUE = 4 shl OP_SHIFT
        internal const val FORK_STRING = "FORK"
        internal const val FORKED_STRING = "FORKED"
        internal const val FORK_SHORT = "f"

        // action = UPDATE
        internal const val MERGE_VALUE = 5 shl OP_SHIFT
        internal const val MERGE_STRING = "MERGE"
        internal const val MERGED_STRING = "MERGED"
        internal const val MERGE_SHORT = "m"

        // action = DELETE | CREATE
        internal const val SPLIT_VALUE = 6 shl OP_SHIFT
        internal const val SPLIT_STRING = "SPLIT"
        internal const val SPLIT_SHORT = "s"

        // action = DELETE | CREATE
        internal const val JOIN_VALUE = 7 shl OP_SHIFT
        internal const val JOIN_STRING = "JOIN"
        internal const val JOINED_STRING = "JOINED"
        internal const val JOIN_SHORT = "j"

        // action = UNDEFINED
        internal const val UNDEFINED_VALUE = 15 shl OP_SHIFT
        internal const val UNDEFINED_STRING = "UNDEFINED"
        internal const val UNDEFINED_SHORT = "x"

        /**
         * The feature was created.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val CREATE = defIgnoreCase(TYPE, CREATE_STRING) { self ->
            self.intValue = CREATE_VALUE
            self.shortId = CREATE_SHORT
            self.action = Action.CREATE
            self.actions = arrayOf(Action.CREATE)
        }.alias<Operation>(CREATED_STRING)

        @Deprecated(
            message = "Deprecated, please use CREATE",
            replaceWith = ReplaceWith("CREATE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val CREATED = CREATE

        /**
         * The feature was updated.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val UPDATE = defIgnoreCase(TYPE, UPDATE_STRING) { self ->
            self.intValue = UPDATE_VALUE
            self.shortId = UPDATE_SHORT
            self.action = Action.UPDATE
            self.actions = arrayOf(Action.UPDATE)
        }.alias<Operation>(UPDATED_STRING)

        @Deprecated(
            message = "Deprecated, please use UPDATE",
            replaceWith = ReplaceWith("UPDATE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val UPDATED = UPDATE

        /**
         * The feature was deleted.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val DELETE = defIgnoreCase(TYPE, DELETE_STRING) { self ->
            self.intValue = DELETE_VALUE
            self.shortId = DELETE_SHORT
            self.action = Action.DELETE
            self.actions = arrayOf(Action.DELETE)
        }.alias<Operation>(DELETED_STRING)

        @Deprecated(
            message = "Deprecated, please use DELETE",
            replaceWith = ReplaceWith("DELETE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val DELETED = DELETE

        /**
         * The feature is created, but originates from another storage, map, or collection, or the `id` of the feature was changed.
         *
         * The [origin][Metadata.origin] will refer to the original [Tuple] that was copied.
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val FORK = defIgnoreCase(TYPE, FORK_STRING) { self ->
            self.intValue = FORK_VALUE
            self.shortId = FORK_SHORT
            self.action = Action.CREATE
            self.actions = arrayOf(Action.CREATE)
        }.alias<Operation>(FORKED_STRING)

        @Deprecated(
            message = "Deprecated, please use FORK",
            replaceWith = ReplaceWith("FORK"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val FORKED = FORK

        /**
         * The feature was [Action.UPDATE], and another client modified the feature concurrently, but the _service_ was able to auto-merge the updates done concurrently with the ones done by the client.
         *
         * The [base_tn][Metadata.baseTupleNumber] refers to the shared base [Tuple] that was modified by this and the foreign _principal_.
         * @see [Metadata.baseTupleNumber]
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val MERGE = defIgnoreCase(TYPE, MERGE_STRING) { self ->
            self.intValue = MERGE_VALUE
            self.shortId = MERGE_SHORT
            self.action = Action.UPDATE
            self.actions = arrayOf(Action.UPDATE)
        }.alias<Operation>(MERGED_STRING)

        @Deprecated(
            message = "Deprecated, please use MERGE",
            replaceWith = ReplaceWith("MERGE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val MERGED = MERGE

        /**
         * The feature was split, the action will be either [CREATED][Action.CREATE] or [DELETED][Action.DELETE].
         *
         * The feature that was split is expected to be deleted, so has [action] set to [DELETED][Action.DELETE], while the new features, being the outcome of the split, are created and have their action set to [CREATED][Action.CREATE].
         *
         * All features being part of a split have the [operation] set to [Operation.SPLIT], and they all will refer to the original feature that was split via [origin][Metadata.origin]. The `origin` refers to the [Tuple] before the split, so **not** to the [Tuple] that is created as the result of the split!
         *
         * To find all features being part of a split, a search for all [Tuple] with the same [origin][Metadata.origin], and operation being `SPLIT`.
         */
        @JsStatic
        @JvmField
        val SPLIT = defIgnoreCase(TYPE, SPLIT_STRING) { self ->
            self.intValue = SPLIT_VALUE
            self.shortId = SPLIT_SHORT
            self.action = null
            self.actions = arrayOf(Action.CREATE, Action.DELETE)
        }

        /**
         * The feature is the result of a join of some features into a new one. The action of the new feature set to [CREATED][Action.CREATE], and the action of all features joined into this new feature is set to [DELETED][Action.DELETE].
         *
         * This _operation_ requires that the client sets the [target][Metadata.target] to the new feature that replaces the deleted features.
         *
         * The [origin][Metadata.origin] of all deleted features will refer to the [Tuple] before the join.
         *
         * To find all features being part of a join, a search for all [Tuple] with the same [target][Metadata.target], and operation being `JOIN`.
         */
        @JsStatic
        @JvmField
        val JOIN = defIgnoreCase(TYPE, JOIN_STRING) { self ->
            self.intValue = JOIN_VALUE
            self.shortId = JOIN_SHORT
            self.action = null
            self.actions = arrayOf(Action.CREATE, Action.DELETE)
        }.alias<Operation>(JOINED_STRING)

        @Deprecated(
            message = "Deprecated, please use JOIN",
            replaceWith = ReplaceWith("JOIN"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val JOINED = JOIN

        /**
         * This [Tuple] is the result of a rebase. The action can be anything, so [CREATED][Action.CREATE], [UPDATED][Action.UPDATE], or [DELETED][Action.DELETE].
         *
         * The values of [origin][Metadata.origin] and [target][Metadata.target] will be adjusted by the rebase to reflect the latest, updated, state.
         *
         * The _REBASE_ operation is to signal that changes done to the [origin][Metadata.origin] have been updated in this version of the feature.
         */
        @JsStatic
        @JvmField
        val REBASE = defIgnoreCase(TYPE, REBASE_STRING) { self ->
            self.intValue = REBASE_VALUE
            self.shortId = REBASE_SHORT
            self.action = null
            self.actions = arrayOf(Action.CREATE, Action.UPDATE, Action.DELETE)
        }.alias<Operation>(REBASED_STRING)

        @Deprecated(
            message = "Deprecated, please use REBASE",
            replaceWith = ReplaceWith("REBASE"),
            level = DeprecationLevel.ERROR
        )
        @JsStatic
        @JvmField
        val REBASED = REBASE

        /**
         * The action is unknown (invalid state).
         */
        @JsStatic
        @JvmField
        val UNDEFINED = defIgnoreCase(TYPE, UNDEFINED_STRING) { self ->
            self.intValue = UNDEFINED_VALUE
            self.shortId = UNDEFINED_SHORT
            self.action = Action.UNDEFINED
        }

        // This supports full-qualified names (that default PlatformEnum support as well) PLUS short notation!
        private val FROM_STRING = mapOf(
            Pair(CREATE_STRING, CREATE), Pair(CREATE_SHORT, CREATE), Pair(CREATED_STRING, CREATE),
            Pair(UPDATE_STRING, UPDATE), Pair(UPDATE_SHORT, UPDATE), Pair(UPDATED_STRING, UPDATE),
            Pair(DELETE_STRING, DELETE), Pair(DELETE_SHORT, DELETE), Pair(DELETED_STRING, DELETE),
            Pair(REBASE_STRING, REBASE), Pair(REBASE_SHORT, REBASE), Pair(REBASED_STRING, REBASE),
            Pair(FORK_STRING, FORK), Pair(FORK_SHORT, FORK), Pair(FORKED_STRING, FORK),
            Pair(MERGE_STRING, MERGE), Pair(MERGE_SHORT, MERGE), Pair(MERGED_STRING, MERGE),
            Pair(SPLIT_STRING, SPLIT), Pair(SPLIT_SHORT, SPLIT),
            Pair(JOIN_STRING, JOIN), Pair(JOIN_SHORT, JOIN), Pair(JOINED_STRING, JOIN),
        )

        private val FROM_VALUE = mapOf(
            Pair(CREATE_VALUE, CREATE),
            Pair(UPDATE_VALUE, UPDATE),
            Pair(DELETE_VALUE, DELETE),
            Pair(REBASE_VALUE, REBASE),
            Pair(FORK_VALUE, FORK),
            Pair(MERGE_VALUE, MERGE),
            Pair(SPLIT_VALUE, SPLIT),
            Pair(JOIN_VALUE, JOIN),
        )

        /**
         * Helper to parse a string into an [Operation].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Operation = FROM_STRING[s] ?: UNDEFINED

        /**
         * Helper to get the [Operation] from the value.
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Int): Operation = FROM_VALUE[value] ?: UNDEFINED
    }

    /**
     * The integer value.
     * @since 3.0
     * @see [fromValue]
     */
    var intValue: Int = UNDEFINED_VALUE
        private set

    /**
     * The short identifier, if there is any.
     * @since 3.0
     */
    var shortId: String = UNDEFINED_STRING
        private set

    /**
     * The [Action] that correlates to this operation, if there is a single action hard-wired with the operation.
     *
     * This is not the case for all operations, for example a [rebase][REBASE] is one operation, but can lead to different actions, as rebasing may create, update, and delete features as a result.
     * @since 3.0
     */
    var action: Action? = null
        private set

    /**
     * The list of all [actions][Action] that are allowed to this operation.
     * @since 3.0
     */
    var actions: Array<Action> = emptyArray()
        private set
}