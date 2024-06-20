/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.auth;

import static java.util.Collections.emptyList;

import java.util.List;

public class Employee extends Person {

  public static final String DEFAULT_MANAGER_NAME = "Scott";
  private Person manager;

  public Employee(String name) {
    super(name, List.of("making_money"));
  }

  // use == get or create
  Person useManager() {
    if (manager == null) {
      setManager(new Person(DEFAULT_MANAGER_NAME, emptyList()));
    }
    return manager;
  }

  public Person getManager() {
    return manager;
  }

  public void setManager(Person manager) {
    this.manager = manager;
  }
}
