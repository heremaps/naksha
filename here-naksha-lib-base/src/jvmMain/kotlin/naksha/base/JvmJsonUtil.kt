package naksha.base

object JvmJsonUtil {

    @JvmStatic
    @JvmOverloads
    fun <T : PAnyMap> readJsonAs(
        json: String,
        type: Class<T>,
        fromJsonOptions: FromJsonOptions = FromJsonOptions.DEFAULT
    ): T? = JvmBoxingUtil.box(Base.fromJSON(json, fromJsonOptions), type)
}