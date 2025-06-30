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
 *       = NotNullBoolProperty<Foo>()
 *     private val BAR_MEMBER
 *       = NotNullBoolProperty<Foo>(storeDefaultValue=true)
 *   }
 *   var admin: Boolean by ADMIN_MEMBER
 *   var bar: Boolean by BAR_MEMBER
 * }
 * ```
 * Same code in Java:
 * ```java
 * public class Foo extends AnyObject() {
 *   private NotNullBoolProperty<Foo> ADMIN_MEMBER
 *     = new NotNullBoolProperty<>("admin");
 *   private NotNullBoolProperty<Foo> BAR_MEMBER
 *     = new NotNullBoolProperty<>("bar", false, true);
 *
 *   // getAdmin in Kotlin
 *   public boolean isAdmin() {
 *      return ADMIN_MEMBER.getValue(this);
 *   }
 *   public void setAdmin(boolean value) {
 *     ADMIN_MEMBER.setValue(value);
 *   }
 *   // getBar in Kotlin
 *   public boolean isBar() {
 *     return BAR_MEMBER.getValue(this);
 *   }
 *   public setBar(boolean value) {
 *     BAR_MEMBER.setValue(value);
 *   }
 * }
 * ```
 * The example above will not store the `admin` property, when it is `false`, it will only keep the information in the JSON, when it is explicitly `true`. For `bar` the opposite is _true_, the value will always been set, but when it does not exist, the default is `false`.
 * @param OBJECT_TYPE The type of the object to which to attach the property.
 * @param name The name of the property in the map, if different from the property name, if _null_, the property name is used.
 * @param defaultValue The default value, when the property does not exist or is no boolean.
 * @param storeDefaultValue If the default value should be stored in the underlying, when being _false_, the property is removed.
 */
open class NotNullBoolProperty<OBJECT_TYPE : AnyObject> @JvmOverloads constructor(
    val name: String? = null,
    val defaultValue: Boolean = false,
    val storeDefaultValue: Boolean = false,
) {
    @JvmOverloads
    open fun getValue(self: OBJECT_TYPE, propertyName: String? = null): Boolean {
        val key = propertyName ?: name ?: throw illegalState("Property name is null")
        return self.getAs(key, Boolean_TYPE) ?: defaultValue
    }

    @JsName("getValueByProperty")
    open operator fun getValue(self: OBJECT_TYPE, property: KProperty<*>): Boolean =
        getValue(self, property.name)

    @JvmOverloads
    open fun setValue(self: OBJECT_TYPE, propertyName: String? = null, value: Boolean?) {
        val key = propertyName ?: name ?: throw illegalState("Property name is null")
        if (!storeDefaultValue && (value == null || value == defaultValue)) {
            self.delete(key)
        } else {
            self.set(key, value)
        }
    }

    @JsName("setValueByProperty")
    open operator fun setValue(self: OBJECT_TYPE, property: KProperty<*>, value: Boolean) =
        setValue(self, property.name, value)
}