package naksha.model.objects

import naksha.base.PTypedMap
import naksha.base.Base.BaseCompanion.UNDEFINED
import naksha.geo.SpGeometry
import naksha.model.Tuple
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.model.objects.MemberType.MemberType_C.SPATIAL
import kotlin.js.JsName

class SpatialMember() : TypedMember<SpatialMember>() {
    override fun verify(): SpatialMember {
        if (dataType != SPATIAL) {
            throw illegalState("The member was illegally cast, expected subtype: $SPATIAL, found: $dataType")
        }
        return this
    }

    /** Creates a new spatial member with the given name and an optional custom JSON path. */
    @JsName("of")
    constructor(name: String, path: JsonPath? = null) : this() {
        this.id = name
        this.dataType = SPATIAL
        this.path = path ?: JsonPath("properties", name)
        this.path.validate()
    }

    /** Creates a spatial member from an existing [Member], validating its type. */
    @JsName("from")
    constructor(member: Member, path: JsonPath? = null) : this() {
        if (member.dataType != SPATIAL) throw illegalArg("The given member is not of spatial type")
        this.id = member.id
        this.dataType = SPATIAL
        this.path = path?.validate() ?: member.path
    }

    /** Retrieves the spatial geometry value of this member from the given feature. */
    fun get(feature: NakshaFeature): SpGeometry? = readGeometry(feature)

    /**
     * Retrieves the spatial geometry value of this member from the given tuple.
     * TODO: When no such member exists in membersBook, should search along [path] in [tuple.featureBytes], but currently cannot due to JbDecoder2 limits.
     */
    @JsName("getFromTuple")
    fun get(tuple: Tuple): SpGeometry? = readGeometry(tuple)

    /** Sets the spatial geometry value of this member on the given object. */
    fun set(feature: PTypedMap<*, *>, value: SpGeometry?): Any? = if (value == null) feature.setPath(UNDEFINED, path) else feature.setPath(value, path)
}
