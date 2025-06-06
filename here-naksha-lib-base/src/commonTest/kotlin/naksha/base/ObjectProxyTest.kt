package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.test.*

class Foo : AnyObject() {
    companion object {
        val NAME = NotNullProperty<Foo, String>(String_TYPE) { _, _ -> "Bernd" }
        val AGE = NotNullProperty<Foo, Int>(Int_Type) { _, _ -> 0 }
        val XYZ = NullableProperty<Foo, String>(String_TYPE, name = "@ns:com:here:xyz")
    }

    var name: String by NAME
    var age: Int by AGE
    var xyz: String? by XYZ
}

class Bar : AnyObject() {
    companion object {
        val FOO = NotNullProperty<Bar, Foo>(forKClass(Foo::class))
        val FOO2 = NullableProperty<Bar, Foo>(forKClass(Foo::class))
    }

    var foo: Foo by FOO
    var foo2: Foo? by FOO2
}

class ObjectProxyTest {
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
        val foo2 = bar.foo
        assertSame(foo, foo2)
        bar.foo.age = 12
        val age = bar.foo.age
        assertEquals(12, age)
        assertFalse(bar.foo.hasRaw("name"))
        assertEquals("Bernd", bar.foo.name)
        assertTrue(bar.foo.hasRaw("name"))
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