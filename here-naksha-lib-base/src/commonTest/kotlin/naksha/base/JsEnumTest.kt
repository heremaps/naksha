@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class JsEnumTest {
    open class Vehicle : JsEnum() {
        companion object {
            val TYPE = forKClass(Vehicle::class)
        }

        override fun namespace() = TYPE
        override fun initClass() {
            register(forKClass(Vehicle::class))
            register(forKClass(Car::class))
            register(forKClass(Truck::class))
        }

        open fun type(): String = "Vehicle"
    }

    class Car : Vehicle() {
        companion object {
            val TYPE = forKClass(Car::class)

            @JvmField
            @JsStatic
            val BAR = def(TYPE, "bar")
        }

        override fun type(): String = "Car"
    }

    class Truck : Vehicle() {
        companion object {
            val TYPE = forKClass(Truck::class)

            @JvmField
            @JsStatic
            val FOO = def(TYPE, "foo")
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

    class MyObject : AnyObject() {
        companion object {
            val TYPE = forKClass(MyObject::class)
            val FOO = NotNullEnum<MyObject, Vehicle>(Vehicle.TYPE)
        }

        var foo: Vehicle by FOO
    }

    @Test
    fun testJsEnumProperty() {
        val myObject = MyObject()
        assertNotNull(myObject.foo)
    }
}