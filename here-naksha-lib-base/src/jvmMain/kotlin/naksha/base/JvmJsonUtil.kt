package naksha.base

object JvmJsonUtil {

    @JvmStatic
    @JvmOverloads
    fun <T : AnyObject> readJsonAs(
        json: String,
        type: Class<T>,
        fromJsonOptions: FromJsonOptions = FromJsonOptions.DEFAULT
    ): T? = JvmBoxingUtil.box(Platform.fromJSON(json, fromJsonOptions), type)
}