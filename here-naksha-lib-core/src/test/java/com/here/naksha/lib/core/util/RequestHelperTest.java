package com.here.naksha.lib.core.util;

import com.here.naksha.lib.core.util.storage.RequestHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RequestHelperTest {

    //TODO(lib-core test) see if this test is still relevant now that PRef is no longer available (replaced with Property?)
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
}
