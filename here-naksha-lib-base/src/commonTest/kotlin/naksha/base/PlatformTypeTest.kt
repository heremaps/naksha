package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import kotlin.test.*

class PlatformTypeTest {
    @Test
    fun testAnyObject() {
        val proxyType = forKClass(Proxy::class)
        assertSame(Proxy.TYPE, proxyType)
        assertTrue(proxyType.isProxy())

        assertTrue(proxyType.isAssignableTo(proxyType))
        assertTrue(proxyType.isAssignableFrom(proxyType))

        val mapProxyType = forKClass(MapProxy::class)
        assertNotNull(mapProxyType)
        assertTrue(mapProxyType.isProxy())

        assertTrue(mapProxyType.isAssignableTo(mapProxyType))
        assertTrue(mapProxyType.isAssignableFrom(mapProxyType))
        assertTrue(mapProxyType.isAssignableTo(proxyType))
        assertTrue(proxyType.isAssignableFrom(proxyType))

        val anyObjectType = forKClass(AnyObject::class)
        assertNotNull(anyObjectType)
        assertTrue(anyObjectType.isProxy())

        assertTrue(anyObjectType.isAssignableTo(anyObjectType))
        assertTrue(anyObjectType.isAssignableFrom(anyObjectType))
        assertTrue(anyObjectType.isAssignableTo(mapProxyType))
        assertTrue(mapProxyType.isAssignableFrom(anyObjectType))
        assertTrue(anyObjectType.isAssignableTo(proxyType))
        assertTrue(proxyType.isAssignableFrom(anyObjectType))
    }

    @Test
    fun testNamespace() {
        val mapProxyType = forKClass(MapProxy::class)
        assertNotNull(mapProxyType)
        assertEquals("MapProxy", mapProxyType.simpleName)
        assertTrue("naksha.base" == mapProxyType.packageName)
        assertTrue("naksha.base.MapProxy" == mapProxyType.name)
    }

    @Test
    fun testPrimitives() {
        assertTrue(Boolean_TYPE.isInstance(true))
        assertTrue(Boolean_TYPE.isInstance(false))
        assertTrue(Int_Type.isInstance(1))
        assertTrue(Int_Type.isInstance(-200000))
        assertTrue(Double_TYPE.isInstance(1.0))
        assertTrue(Double_TYPE.isInstance(-500.123))
        val i64 = Int64(100L)
        assertTrue(Int64_TYPE.isInstance(i64))
    }
}