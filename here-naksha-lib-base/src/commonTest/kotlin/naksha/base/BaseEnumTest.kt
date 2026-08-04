@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BaseEnumTest {
    open class Vehicle : BaseEnum() {

        override fun namespace(): KClass<out BaseEnum> = Vehicle::class
        override fun initClass() {
            register(Vehicle::class)
            register(Car::class)
            register(Truck::class)
        }

        open fun type(): String = "Vehicle"
    }

    class Car : Vehicle() {
        companion object {
            @JvmField
            @JsStatic
            val BAR = def(Car::class, "bar")
        }

        override fun type(): String = "Car"
    }

    class Truck : Vehicle() {
        companion object {
            @JvmField
            @JsStatic
            val FOO = def(Truck::class, "foo")
        }

        override fun type(): String = "Truck"
    }

    @Test
    fun testJsEnumExample() {
        // Tests the code given as example in the JsEnum class!
        //Platform.logger.info("bar: {}", Car.BAR)
        //Platform.logger.info("foo: {}", Truck.FOO)
        val bar = BaseEnum.get("bar", Vehicle::class)
        assertSame(Car.BAR, bar)
        val foo = BaseEnum.get("foo", Vehicle::class)
        assertSame(Truck.FOO, foo)
        val unknown = BaseEnum.get("unknown", Vehicle::class)
        assertEquals("bar is Car", "$bar is ${bar.type()}")
        assertEquals("foo is Truck", "$foo is ${foo.type()}")
        assertEquals("unknown is Vehicle", "$unknown is ${unknown.type()}")
    }
}