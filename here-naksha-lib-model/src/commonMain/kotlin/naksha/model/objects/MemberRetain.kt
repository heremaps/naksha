package naksha.model.objects

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * The enumeration of the possible retain strategy for [Member] values.
 * @since 3.0
 * @see Member
 * @see ON
 * @see OFF
 * @see ALWAYS
 */
@JsExport
class MemberRetain : JsEnum() {
    override fun namespace(): KClass<out JsEnum> = MemberRetain::class
    override fun initClass() {}

    companion object MemberRetain_C {
        /**
         * The member value should retain in the feature at the configured [JsonPath], if not matching the desired [MemberType]. If the value can be extracted lossless, then the path is removed from the feature bytes.
         * @since 3.0
         * @see MemberRetain
         */
        @JvmField
        val ON = defIgnoreCase(MemberRetain::class, "on")

        /**
         * The member value should not retain in the feature at the configured [JsonPath], even if it does not match the desired [MemberType]. This causes data loss, because values not matching the type are removed when encoding.
         * @since 3.0
         * @see MemberRetain
         */
        @JvmField
        val OFF = defIgnoreCase(MemberRetain::class, "off")

        /**
         * The member value should always retain in the feature at the configured [JsonPath], even if the value can be extracted lossless. This duplicates storage size, but guarantees that the value is always persisted as is.
         * @since 3.0
         * @see MemberRetain
         */
        @JvmField
        val ALWAYS = defIgnoreCase(MemberRetain::class, "always")
    }
}