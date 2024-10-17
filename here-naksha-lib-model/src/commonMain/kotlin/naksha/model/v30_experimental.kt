package naksha.model

/**
 * An annotation for all experimental code being part of v3.0.0, that may or may not become part of the final API later.
 *
 * Using experimental code may result in change requirements later, as it may not end up in the final API, or may be subject to incompatible changes.
 * @since 3.0.0
 */
@RequiresOptIn(message = "This API is experimental. It may be changed in the future without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.TYPEALIAS
)
annotation class v30_experimental
