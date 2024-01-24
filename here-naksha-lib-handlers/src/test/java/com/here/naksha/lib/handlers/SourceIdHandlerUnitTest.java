package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.models.storage.NonIndexedPRef;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SourceIdHandlerUnitTest {

    private static final String NS_COM_HERE_MOM_META = "@ns:com:here:mom:meta";

    @Test
    void tc2002_testMapEqToContainsTag() {
        //given
        NonIndexedPRef pRef = new NonIndexedPRef("properties", NS_COM_HERE_MOM_META, "sourceId");
        POp given = POp.eq(pRef, "task_1");
        //when

        Optional<POp> result = SourceIdHandler.transformPopWithSourceId(given);
        //then

        assertTrue(result.isPresent());
        assertEquals(result.get().getPropertyRef().getTagName(), "xyz_source_id_task_1");
        assertEquals(result.get().op(), POpType.EXISTS);
    }

    @Test
    void tc2003_testMapNotEqToNotContainsTag() {
        //given
        NonIndexedPRef pRef = new NonIndexedPRef("properties", NS_COM_HERE_MOM_META, "sourceId");
        POp given = POp.not(POp.eq(pRef, "task_1"));
        //when

        Optional<POp> result = SourceIdHandler.transformPopWithSourceId(given);
        //then

        assertTrue(result.isPresent());
        assertFalse(result.get().children().isEmpty());
        POp nestedPop = result.get().children().get(0);

        assertEquals(nestedPop.getPropertyRef().getTagName(), "xyz_source_id_task_1");
        assertEquals(nestedPop.op(), POpType.EXISTS);
    }

    @Test
    void tc2004_testMapContainsToContainsTag() {
        //given
        NonIndexedPRef pRef = new NonIndexedPRef("properties", NS_COM_HERE_MOM_META, "sourceId");
        POp given = POp.contains(pRef, "task_1");
        //when

        Optional<POp> result = SourceIdHandler.transformPopWithSourceId(given);
        //then

        assertTrue(result.isPresent());
        assertEquals(result.get().getPropertyRef().getTagName(), "xyz_source_id_task_1");
        assertEquals(result.get().op(), POpType.EXISTS);
    }

    @Test
    void tc2004_testMapOnlyCorrectPref() {
        //given
        NonIndexedPRef pRef = new NonIndexedPRef("properties", NS_COM_HERE_MOM_META, "WrongPRef");
        POp given = POp.eq(pRef, "task_1");
        //when

        Optional<POp> result = SourceIdHandler.transformPopWithSourceId(given);
        //then

        assertTrue(result.isEmpty());
    }
}