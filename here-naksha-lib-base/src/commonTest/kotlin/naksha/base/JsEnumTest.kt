@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JsEnumTest {
    open class Vehicle protected constructor(value: String) : JsEnum(value) {
        override fun initClass() {
            register(Vehicle::class, Vehicle::class)
            register(Car::class, Vehicle::class)
            register(Truck::class, Vehicle::class)
        }

        override fun namespace(): KClass<out JsEnum> = Vehicle::class
        override fun init() {}
        open fun type(): String = "Vehicle"
    }

    class Car private constructor(value: String) : Vehicle(value) {
        companion object {
            @JvmStatic
            @JsStatic
            val BAR = Car("bar")
        }

        override fun type(): String = "Car"
    }

    class Truck private constructor(value: String) : Vehicle(value) {
        companion object {
            @JvmStatic
            @JsStatic
            val FOO = Truck("foo")
        }

        override fun type(): String = "Truck"
    }

    @Test
    fun testJsEnumExample() {
        // Tests the code given as example in the JsEnum class!
        val bar = JsEnum.get("bar", Vehicle::class)
        assertSame(Car.BAR, bar)
        val foo = JsEnum.get("foo", Vehicle::class)
        assertSame(Truck.FOO, foo)
        val unknown = JsEnum.get("unknown", Vehicle::class)
        assertEquals("bar is Car", "$bar is ${bar.type()}")
        assertEquals("foo is Truck", "$foo is ${foo.type()}")
        assertEquals("unknown is Vehicle", "$unknown is ${unknown.type()}")
    }
}