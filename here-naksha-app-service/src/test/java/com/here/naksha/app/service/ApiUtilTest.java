/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.here.naksha.app.service.http.apis.ApiUtil;
import com.here.naksha.lib.core.models.storage.POp;
import com.here.naksha.lib.core.models.storage.POpType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ApiUtilTest {

  @Test
  public void testBuildOperationForTagList() {
    final List<String> tagList = new ArrayList<>();
    tagList.add("one");
    tagList.add("two%2CTHREE%2C@Four");
    tagList.add("five%2B@sIx%2Bseven");
    tagList.add("eight");

    final POp op = ApiUtil.buildOperationForTagList(tagList);
    assertEquals(POpType.OR, op.op());
    final List<POp> orList = op.children();

    // ensure there are total 4 operations
    assertNotNull(orList, "Expected multiple nested operations");
    assertEquals(4, orList.size());

    // validate first operation uses EXISTS
    assertEquals(POpType.EXISTS, orList.get(0).op());
    assertEquals("one", orList.get(0).getPropertyRef().getTagName());

    // validate second operation uses OR
    assertEquals(POpType.OR, orList.get(1).op());
    final List<POp> secondOpList = orList.get(1).children();
    assertNotNull(secondOpList, "Expected multiple nested operations");
    assertEquals(3, secondOpList.size());
    assertEquals(POpType.EXISTS, secondOpList.get(0).op());
    assertEquals("two", secondOpList.get(0).getPropertyRef().getTagName());
    assertEquals(POpType.EXISTS, secondOpList.get(1).op());
    assertEquals("three", secondOpList.get(1).getPropertyRef().getTagName());
    assertEquals(POpType.EXISTS, secondOpList.get(2).op());
    assertEquals("@Four", secondOpList.get(2).getPropertyRef().getTagName());

    // validate third operation uses AND
    assertEquals(POpType.AND, orList.get(2).op());
    final List<POp> thirdOpList = orList.get(2).children();
    assertNotNull(thirdOpList, "Expected multiple nested operations");
    assertEquals(3, thirdOpList.size());
    assertEquals(POpType.EXISTS, thirdOpList.get(0).op());
    assertEquals("five", thirdOpList.get(0).getPropertyRef().getTagName());
    assertEquals(POpType.EXISTS, thirdOpList.get(1).op());
    assertEquals("@sIx", thirdOpList.get(1).getPropertyRef().getTagName());
    assertEquals(POpType.EXISTS, thirdOpList.get(2).op());
    assertEquals("seven", thirdOpList.get(2).getPropertyRef().getTagName());

    // validate forth operation uses EXISTS
    assertEquals(POpType.EXISTS, orList.get(3).op());
    assertEquals("eight", orList.get(3).getPropertyRef().getTagName());
  }
}
