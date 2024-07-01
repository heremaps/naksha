package naksha.base

import kotlin.test.*

class Foo : ObjectProxy() {
    companion object {
        val NAME = NotNullProperty<Any, Foo, String>(String::class, { "Bernd" })
        val AGE = NotNullProperty<Any, Foo, Int>(Int::class, { 0 })
        val XYZ = NullableProperty<Any, Foo, String>(String::class, name = "@ns:com:here:xyz")
    }

    var name: String by NAME
    var age: Int by AGE
    var xyz: String? by XYZ
}

class Bar : ObjectProxy() {
    companion object {
        val FOO = NotNullProperty<Any, Bar, Foo>(Foo::class)
        val FOO2 = NullableProperty<Any, Bar, Foo>(Foo::class)
    }

    var foo: Foo by FOO
    var foo2: Foo? by FOO2
}

class ObjectProxyTest {
    @BeforeTest
    fun beforeAll() {
        Foo.Companion
        Foo.NAME
        Foo.AGE
        Foo.XYZ
        Bar.Companion
        Bar.FOO
        Bar.FOO2
    }

    @Test
    fun testSingleton() {
        val foo1 = Foo()
        foo1.name = "a"
        val foo2 = Foo()
        foo2.name = "a"
        // Set a breakpoint here.
        // There should be no delegation, the compiler should inline the setter and getter.
        assertNotSame(foo1, foo2)
    }

    @Test
    fun testNotNullable() {
        val bar = Bar()
        val foo = bar.foo
        assertNotNull(foo)
        assertSame(foo, bar.foo)
        Platform.logger.info("-- 3 --: {}", bar)
        Platform.logger.info("-- 3 --: {}", bar.foo.age)
        bar.foo.age = 12
        Platform.logger.info("-- 4 --")
        assertEquals(12, bar.foo.age)
        Platform.logger.info("-- 5 --")
        assertFalse(bar.foo.hasRaw("name"))
        Platform.logger.info("-- 6 --")
        assertEquals("Bernd", bar.foo.name)
        Platform.logger.info("-- 7 --")
        assertTrue(bar.foo.hasRaw("name"))
        Platform.logger.info("-- 8 --")
        assertEquals("Bernd", bar.foo.getRaw("name"))
        bar.foo.name = "Hello World"
        assertEquals("Hello World", bar.foo.name)
    }

    @Test
    fun testRenaming() {
        val bar = Bar()
        assertNull(bar.foo.xyz)
        bar.foo.xyz = "Test renaming"
        assertEquals("Test renaming", bar.foo.xyz)
        assertNull(bar.foo.getRaw("xyz"))
        assertEquals("Test renaming", bar.foo.getRaw("@ns:com:here:xyz"))
    }

    @Test
    fun testNullable() {
        val bar = Bar()
        assertNull(bar.foo2)
        bar.foo2 = Foo()
        val foo2 = bar.foo2
        assertNotNull(foo2)
        assertSame(foo2, bar.foo2)
    }
}