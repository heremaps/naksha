package naksha.model

import naksha.base.JsEnum
import naksha.model.FlagsBits.FlagsBitsCompanion.OP_SHIFT
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * An enumeration about the operation being performed.
 *
 * This describes in greater detail what was logically done to feature, not what the effect was, which is described by the [Action].
 * @since 3.0.0
 * @see [Action]
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class Operation : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = Action::class

    override fun initClass() {}

    @Suppress("MemberVisibilityCanBePrivate")
    companion object Operation_C {
        // action = CREATED
        internal const val CREATED_VALUE = 0 shl OP_SHIFT
        internal const val CREATED_STRING = "CREATED"
        internal const val CREATED_SHORT = "c"

        // action = UPDATED
        internal const val UPDATED_VALUE = 1 shl OP_SHIFT
        internal const val UPDATED_STRING = "UPDATED"
        internal const val UPDATED_SHORT = "u"

        // action = DELETED
        internal const val DELETED_VALUE = 2 shl OP_SHIFT
        internal const val DELETED_STRING = "DELETED"
        internal const val DELETED_SHORT = "d"

        // action = DELETED | UPDATED | CREATED
        internal const val REBASED_VALUE = 3 shl OP_SHIFT
        internal const val REBASED_STRING = "REBASED"
        internal const val REBASED_SHORT = "b"

        // ---------------------------< extending action >----------------------------------------

        // action = CREATED
        internal const val FORKED_VALUE = 4 shl OP_SHIFT
        internal const val FORKED_STRING = "FORKED"
        internal const val FORKED_SHORT = "f"

        // action = UPDATED
        internal const val MERGED_VALUE = 5 shl OP_SHIFT
        internal const val MERGED_STRING = "MERGED"
        internal const val MERGED_SHORT = "m"

        // action = DELETED | CREATED
        internal const val SPLIT_VALUE = 6 shl OP_SHIFT
        internal const val SPLIT_STRING = "SPLIT"
        internal const val SPLIT_SHORT = "s"

        // action = DELETED | CREATED
        internal const val JOINED_VALUE = 7 shl OP_SHIFT
        internal const val JOINED_STRING = "JOINED"
        internal const val JOINED_SHORT = "j"

        // action = UNDEFINED
        internal const val UNDEFINED_VALUE = 15 shl OP_SHIFT
        internal const val UNDEFINED_STRING = "UNDEFINED"
        internal const val UNDEFINED_SHORT = "x"

        /**
         * The feature was created.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val CREATED = defIgnoreCase(Operation::class, CREATED_STRING) { self ->
            self.intValue = CREATED_VALUE
            self.shortId = CREATED_SHORT
            self.action = Action.CREATED
            self.actions = arrayOf(Action.CREATED)
        }

        /**
         * The feature was updated.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val UPDATED = defIgnoreCase(Operation::class, UPDATED_STRING) { self ->
            self.intValue = UPDATED_VALUE
            self.shortId = UPDATED_SHORT
            self.action = Action.UPDATED
            self.actions = arrayOf(Action.UPDATED)
        }

        /**
         * The feature was deleted.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val DELETED = defIgnoreCase(Operation::class, DELETED_STRING) { self ->
            self.intValue = DELETED_VALUE
            self.shortId = DELETED_SHORT
            self.action = Action.DELETED
            self.actions = arrayOf(Action.DELETED)
        }

        /**
         * The feature is created, but originates from another storage, map, or collection, or the `id` of the feature was changed.
         *
         * The [origin][Metadata.origin] will refer to the original [Tuple] that was copied.
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val FORKED = defIgnoreCase(Operation::class, FORKED_STRING) { self ->
            self.intValue = FORKED_VALUE
            self.shortId = FORKED_SHORT
            self.action = Action.CREATED
            self.actions = arrayOf(Action.CREATED)
        }

        /**
         * The feature was [Action.UPDATED], and another client modified the feature concurrently, but the _service_ was able to auto-merge the updates done concurrently with the ones done by the client.
         *
         * The [base_tn][Metadata.baseTupleNumber] refers to the shared base [Tuple] that was modified by this and the foreign _principal_.
         * @see [Metadata.baseTupleNumber]
         * @since 3.0.0
         */
        @JsStatic
        @JvmField
        val MERGED = defIgnoreCase(Operation::class, MERGED_STRING) { self ->
            self.intValue = MERGED_VALUE
            self.shortId = MERGED_SHORT
            self.action = Action.UPDATED
            self.actions = arrayOf(Action.UPDATED)
        }

        /**
         * The feature was split, the action will be either [CREATED][Action.CREATED] or [DELETED][Action.DELETED].
         *
         * The feature that was split is expected to be deleted, so has [action] set to [DELETED][Action.DELETED], while the new features, being the outcome of the split, are created and have their action set to [CREATED][Action.CREATED].
         *
         * All features being part of a split have the [operation] set to [Operation.SPLIT], and they all will refer to the original feature that was split via [origin][Metadata.origin]. The `origin` refers to the [Tuple] before the split, so **not** to the [Tuple] that is created as the result of the split!
         *
         * To find all features being part of a split, a search for all [Tuple] with the same [origin][Metadata.origin], and operation being `SPLIT`.
         */
        @JsStatic
        @JvmField
        val SPLIT = defIgnoreCase(Operation::class, SPLIT_STRING) { self ->
            self.intValue = SPLIT_VALUE
            self.shortId = SPLIT_SHORT
            self.action = null
            self.actions = arrayOf(Action.CREATED, Action.DELETED)
        }

        /**
         * The feature is the result of a join of some features into a new one. The action of the new feature set to [CREATED][Action.CREATED], and the action of all features joined into this new feature is set to [DELETED][Action.DELETED].
         *
         * This _operation_ requires that the client sets the [target][Metadata.target] to the new feature that replaces the deleted features.
         *
         * The [origin][Metadata.origin] of all deleted features will refer to the [Tuple] before the join.
         *
         * To find all features being part of a join, a search for all [Tuple] with the same [target][Metadata.target], and operation being `JOIN`.
         */
        @JsStatic
        @JvmField
        val JOINED = defIgnoreCase(Operation::class, JOINED_STRING) { self ->
            self.intValue = JOINED_VALUE
            self.shortId = JOINED_SHORT
            self.action = null
            self.actions = arrayOf(Action.CREATED, Action.DELETED)
        }

        /**
         * This [Tuple] is the result of a rebase. The action can be anything, so [CREATED][Action.CREATED], [UPDATED][Action.UPDATED], or [DELETED][Action.DELETED].
         *
         * The values of [origin][Metadata.origin] and [target][Metadata.target] will be adjusted by the rebase to reflect the latest, updated, state.
         *
         * The _REBASE_ operation is to signal that changes done to the [origin][Metadata.origin] have been updated in this version of the feature.
         */
        @JsStatic
        @JvmField
        val REBASED = defIgnoreCase(Operation::class, REBASED_STRING) { self ->
            self.intValue = REBASED_VALUE
            self.shortId = REBASED_SHORT
            self.action = null
            self.actions = arrayOf(Action.CREATED, Action.UPDATED, Action.DELETED)
        }

        /**
         * The action is unknown (invalid state).
         */
        @JsStatic
        @JvmField
        val UNDEFINED = defIgnoreCase(Operation::class, UNDEFINED_STRING) { self ->
            self.intValue = UNDEFINED_VALUE
            self.shortId = UNDEFINED_SHORT
            self.action = Action.UNDEFINED
        }

        // This supports full-qualified names (that default JsEnum support as well) PLUS short notation!
        private val FROM_STRING = mapOf(
            Pair(CREATED_STRING, CREATED), Pair(CREATED_SHORT, CREATED),
            Pair(UPDATED_STRING, UPDATED), Pair(UPDATED_SHORT, UPDATED),
            Pair(DELETED_STRING, DELETED), Pair(DELETED_SHORT, DELETED),
            Pair(REBASED_STRING, REBASED), Pair(REBASED_SHORT, REBASED),
            Pair(FORKED_STRING, FORKED), Pair(FORKED_SHORT, FORKED),
            Pair(MERGED_STRING, MERGED), Pair(MERGED_SHORT, MERGED),
            Pair(SPLIT_STRING, SPLIT), Pair(SPLIT_SHORT, SPLIT),
            Pair(JOINED_STRING, JOINED), Pair(JOINED_SHORT, JOINED),
        )

        private val FROM_VALUE = mapOf(
            Pair(CREATED_VALUE, CREATED),
            Pair(UPDATED_VALUE, UPDATED),
            Pair(DELETED_VALUE, DELETED),
            Pair(REBASED_VALUE, REBASED),
            Pair(FORKED_VALUE, FORKED),
            Pair(MERGED_VALUE, MERGED),
            Pair(SPLIT_VALUE, SPLIT),
            Pair(JOINED_VALUE, JOINED),
        )

        /**
         * Helper to parse a string into an [Operation].
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun fromString(s: String): Operation = FROM_STRING[s] ?: UNDEFINED

        /**
         * Helper to get the [Operation] from the value.
         * @since 3.0.0
         */
        @JsStatic
        @JvmStatic
        fun fromValue(value: Int): Operation = FROM_VALUE[value] ?: UNDEFINED
    }

    /**
     * The integer value.
     * @since 3.0.0
     * @see [fromValue]
     */
    var intValue: Int = UNDEFINED_VALUE
        private set

    /**
     * The short identifier, if there is any.
     * @since 3.0.0
     */
    var shortId: String = UNDEFINED_STRING
        private set

    /**
     * The [Action] that correlates to this operation, if there is a single action hard-wired with the operation.
     *
     * This is not the case for all operations, for example a [rebase][REBASED] is one operation, but can lead to different actions, as rebasing may create, update, and delete features as a result.
     * @since 3.0.0
     */
    var action: Action? = null
        private set

    /**
     * The list of all [actions][Action] that are allowed to this operation.
     * @since 3.0.0
     */
    var actions: Array<Action> = emptyArray()
        private set
}