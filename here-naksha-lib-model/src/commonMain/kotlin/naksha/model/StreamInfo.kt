package naksha.model

class StreamInfo {
    var spaceId: String? = null
    var storageId: String? = null
    fun setSpaceIdIfMissing(spaceId: String?) {
        if (this.spaceId == null) this.spaceId = spaceId
    }

    fun setStorageIdIfMissing(storageId: String?) {
        if (this.storageId == null) this.storageId = storageId
    }
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || StreamInfo::class != o::class) return false
        val that = o as StreamInfo
        return spaceId == that.spaceId && storageId == that.storageId
    }

    override fun hashCode(): Int {
        return arrayOf(spaceId, storageId).contentHashCode()
    }

    fun toColonSeparatedString(): String {
        return ("spaceId=" + (if ((spaceId == null || spaceId!!.isEmpty())) "-" else spaceId) + ";storageId="
                + (if ((storageId == null || storageId!!.isEmpty())) "-" else storageId))
    }
}