// TODO: clean up as part of CASL-784
// see if this test is still relevant now that PRef is no longer available (replaced with Property?)
//
//package com.here.naksha.lib.core.util;
//
//import com.here.naksha.lib.core.util.storage.RequestHelper;
//
//public class RequestHelperTest {
//
//
//    @Test
//    public void testPRefFromStandardPath() {
//        final PRef pref = RequestHelper.pRefFromPropPath(new String[]{"properties","@ns:com:here:xyz","tags"});
//        assertNotNull(pref);
//        assertFalse(pref instanceof NON_INDEXED_PREF, "Must be instanceof PRef");
//    }
//
//    @Test
//    public void testPRefFromNonStandardPath() {
//        final PRef pref = RequestHelper.pRefFromPropPath(new String[]{"properties","prop_1"});
//        assertNotNull(pref);
//        assertTrue(pref instanceof NON_INDEXED_PREF, "Must be instanceof NonIndexedPRef");
//    }
//}
