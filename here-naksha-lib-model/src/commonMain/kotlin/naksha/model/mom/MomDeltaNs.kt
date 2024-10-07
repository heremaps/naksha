@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.AnyObject
import kotlin.js.JsExport

@JsExport
class MomDeltaNs : AnyObject() {

    companion object NakshaDeltaProxyCompanion {
        private val STRING_NULL = NullableProperty<MomDeltaNs, String>(String::class)
        private val CHANGE_STATE = NotNullProperty<MomDeltaNs, String>(String::class) { _, _ -> MomChangeState.CREATED.text }
        private val REVIEW_STATE = NotNullProperty<MomDeltaNs, String>(String::class) { _, _ -> MomReviewState.UNPUBLISHED.text }
        private val INT64 = NotNullProperty<MomDeltaNs, Int64>(Int64::class) { _, _ -> Int64(0) }
    }

    /**
     * The origin-ID is a write-once value and must refer to an existing base object. Therefore, there is no guarantee that when this property
     * is set, the corresponding base object does exist, it is only guaranteed that this object existed at the time the origin-ID was set. If
     * a base object is modified the originId is always set automatically and refers to itself.
     */
    var originId: String? by STRING_NULL

    /**
     * When a feature is split, all children must have the <b>parentLink</b> property referring to the feature that has the <b>changeState</b>
     * set to <b>SPLIT</b>. If an object has a <b>parentLink</b> property and the object referred does have an <b>originId</b> set, then this
     * children will automatically derive the <b>originId</b> value. This tracks all changes done back to the original object being modified.
     */
    var parentLink: String? by STRING_NULL

    var changeState: String by CHANGE_STATE

    var reviewState: String by REVIEW_STATE

    /**
     * This value is currently set to 0 as default values. Any client operating in <b>MODERATION</b> or <b>BOT</b> mode can set this value to
     * whatever value he wants; normal users may not explicitly set this property, therefore for them the value is kept as it is, when not
     * existing, it is set 0 for normal users.
     */
    var potentialValue: Int64 by INT64

    /**
     * The priority category assigned to this edit. A value between 0 (no priority, no SLA) and 9. It is never valid to decrease the value
     * (see <a href="https://devzone.it.here.com/jira/browse/CMECMSSUP-1945">CMECMSSUP-1945</a>)!
     */
    var priorityCategory: Int64 by INT64

    /**
     * The UNIX epoch timestamp in milliseconds of the time until when the edit must be taken care of. This property is only set automatically
     * for edits. If the SLA is {@code null} or the <b>priorityCategory</b> is 0 (so, no priority), then the value will be set to 0; otherwise
     * the `meta::lastUpdatedTS` time plus the `SLA::dueIn` value will be taken to calculate this value (except the `SLA::dueIn` is 0, then
     * `meta::dueTS` is as well 0). It is never valid to increase the value (see <a
     * href="https://devzone.it.here.com/jira/browse/CMECMSSUP-1945">CMECMSSUP-1945</a>). We treat 0 as the highest value, therefore it can
     * not override any other **dueTS** value. Be aware that the `SLA::dueIn` is set in seconds, while the **dueTS** property is set in
     * milliseconds.
     */
    var dueTS: String? by STRING_NULL

    fun getChangeStateEnum(): MomChangeState = MomChangeState.of(changeState)

    fun setChangeStateEnum(enumValue: MomChangeState) {
        changeState = enumValue.text
    }

    fun getReviewStateEnum(): MomReviewState = MomReviewState.of(reviewState)

    fun setReviewStateEnum(enumValue: MomReviewState) {
        reviewState = enumValue.text
    }
}