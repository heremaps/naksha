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
package com.here.naksha.handler.activitylog;

import com.here.naksha.lib.core.util.diff.InsertOp;
import com.here.naksha.lib.core.util.diff.RemoveOp;
import com.here.naksha.lib.core.util.diff.UpdateOp;
import java.util.ArrayList;
import java.util.List;

public record ActivityLogReversePatch(int add, int copy, int move, int remove, int replace, List<ReverseOp> ops) {

  record ReverseOp(String name, String path, Object value) {

    static final String REVERSE_INSERT = "remove";
    static final String REVERSE_REMOVE = "insert";

    static final String REVERSE_UPDATE = "update"; // TODO: there was a 'replace' before

    static ReverseOp reverseOf(InsertOp insertOp, String path) {
      return new ReverseOp(REVERSE_INSERT, path, null);
    }

    static ReverseOp reverseOf(RemoveOp removeOp, String path) {
      return new ReverseOp(REVERSE_REMOVE, path, removeOp.oldValue());
    }

    static ReverseOp reverseOf(UpdateOp updateOp, String path) {
      return new ReverseOp(REVERSE_UPDATE, path, updateOp.oldValue());
    }

    @Override
    public String toString() {
      return "ReverseOp{" + "name='" + name + '\'' + ", path='" + path + '\'' + ", value=" + value + '}';
    }
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    int add;
    int remove;
    int replace;
    List<ReverseOp> ops;

    private Builder() {
      add = 0;
      remove = 0;
      replace = 0;
      ops = new ArrayList<>();
    }

    ActivityLogReversePatch build() {
      return new ActivityLogReversePatch(add, 0, 0, remove, replace, ops); // TODO: copy & move?
    }

    Builder reverseInsert(InsertOp insertOp, String path) {
      remove++;
      ops.add(ReverseOp.reverseOf(insertOp, path));
      return this;
    }

    Builder reverseRemove(RemoveOp removeOp, String path) {
      add++;
      ops.add(ReverseOp.reverseOf(removeOp, path));
      return this;
    }

    Builder reverseUpdate(UpdateOp updateOp, String path) {
      replace++; // TODO: sure?
      ops.add(ReverseOp.reverseOf(updateOp, path));
      return this;
    }
  }
}
