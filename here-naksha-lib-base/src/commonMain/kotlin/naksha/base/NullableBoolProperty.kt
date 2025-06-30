package naksha.base

import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KProperty

/**
 * A special boolean property handler for a property of [AnyObject].
 *
 * It is recommended to add this delegator statically to avoid that for every object instance a new instance of the delegator is created.
 * The Kotlin compiler will even inline the getter/setter calls in that case:
 * ```kotlin
 * class Foo : AnyObject() {
 *   companion object Foo_C {
 *     private val ADMIN_MEMBER
 *       = NullableBoolProperty<Foo>()
 *     private val BAR_MEMBER
 *       = NullableBoolProperty<Foo>(storeNull=true)
 *   }
 *   var admin: Boolean by ADMIN_MEMBER
 *   var bar: Boolean by BAR_MEMBER
 * }
 * ```
 * Same code in Java:
 * ```java
 * public class Foo extends AnyObject() {
 *   private NullableBoolProperty<Foo> ADMIN_MEMBER
 *     = new NullableBoolProperty<>("admin");
 *   private NullableBoolProperty<Foo> BAR_MEMBER
 *     = new NullableBoolProperty<>("bar", true);
 *
 *   public @Nullable Boolean getAdmin() {
 *      return ADMIN_MEMBER.getValue(this);
 *   }
 *   public void setAdmin(@Nullable Boolean value) {
 *     ADMIN_MEMBER.setValue(value);
 *   }
 *   public @Nullable Boolean getBar() {
 *     return BAR_MEMBER.getValue(this);
 *   }
 *   public setBar(@Nullable Boolean value) {
 *     BAR_MEMBER.setValue(value);
 *   }
 * }
 * ```
 * The example above will not store the `admin` property, when it is `null`, it will only keep the information in the JSON, when it is explicitly `true` or `false`. For `bar` the opposite is _true_, the value will always been stored, even when it is `null`.
 * @param OBJECT_TYPE The type of the object to which to attach the property.
 * @param name The name of the property in the map, if different from the property name, if _null_, the property name is used.
 * @param storeNull If the value `null` should be stored in the underlying, when explicitly set to _true_, the value `null` is stored, otherwise _(default)_ the property is removed _(becomes undefined)_.
 */
open class NullableBoolProperty<OBJECT_TYPE : AnyObject> @JvmOverloads constructor(
    val name: String? = null,
    val storeNull: Boolean = false,
) {
    @JvmOverloads
    open fun getValue(self: OBJECT_TYPE, propertyName: String? = null): Boolean? {
        val key = propertyName ?: name ?: throw illegalState("Property name is null")
        return self.getAs(key, Boolean_TYPE)
    }

    @JsName("getValueByProperty")
    open operator fun getValue(self: OBJECT_TYPE, property: KProperty<*>): Boolean? =
        getValue(self, property.name)

    @JvmOverloads
    open fun setValue(self: OBJECT_TYPE, propertyName: String? = null, value: Boolean?) {
        val key = propertyName ?: name ?: throw illegalState("Property name is null")
        if (value == null && !storeNull) {
            self.delete(key)
        } else {
            self.set(key, value)
        }
    }

    @JsName("setValueByProperty")
    open operator fun setValue(self: OBJECT_TYPE, property: KProperty<*>, value: Boolean?) =
        setValue(self, property.name, value)
}