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
package com.here.naksha.lib.extmanager.models;

import com.here.naksha.lib.core.models.features.Extension;

public class ExtensionMapper {
  Extension extension;
  ClassLoader classLoader;
  Object classObject;

  public ExtensionMapper(Extension extension, ClassLoader classLoader) {
    this.extension = extension;
    this.classLoader = classLoader;
  }

  public ExtensionMapper(Extension extension, ClassLoader classLoader, Object object) {
    this(extension, classLoader);
    this.classObject = object;
  }

  public Extension getExtension() {
    return extension;
  }

  public void setExtension(Extension extension) {
    this.extension = extension;
  }

  public ClassLoader getClassLoader() {
    return classLoader;
  }

  public void setClassLoader(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public Object getClassObject() {
    return classObject;
  }

  public void setClassObject(Object classObject) {
    this.classObject = classObject;
  }
}
