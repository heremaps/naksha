@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class JsEnumTest {
    open class Vehicle : JsEnum() {

        override fun namespace(): PlatformType<out JsEnum> = forKClass(Vehicle::class)
        override fun initClass() {
            register(forKClass(Vehicle::class))
            register(forKClass(Car::class))
            register(forKClass(Truck::class))
        }

        open fun type(): String = "Vehicle"
    }

    class Car : Vehicle() {
        companion object {
            @JvmField
            @JsStatic
            val BAR = def(forKClass(Car::class), "bar")
        }

        override fun type(): String = "Car"
    }

    class Truck : Vehicle() {
        companion object {
            @JvmField
            @JsStatic
            val FOO = def(forKClass(Truck::class), "foo")
        }

        override fun type(): String = "Truck"
    }

    @Test
    fun testJsEnumExample() {
        // Tests the code given as example in the JsEnum class!
        //Platform.logger.info("bar: {}", Car.BAR)
        //Platform.logger.info("foo: {}", Truck.FOO)
        val bar = JsEnum.get("bar", forKClass(Vehicle::class))
        assertSame(Car.BAR, bar)
        val foo = JsEnum.get("foo", forKClass(Vehicle::class))
        assertSame(Truck.FOO, foo)
        val unknown = JsEnum.get("unknown", forKClass(Vehicle::class))
        assertEquals("bar is Car", "$bar is ${bar.type()}")
        assertEquals("foo is Truck", "$foo is ${foo.type()}")
        assertEquals("unknown is Vehicle", "$unknown is ${unknown.type()}")
    }
}