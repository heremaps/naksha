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
package com.here.naksha.lib.core.util;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;

/**
 * Grant access to the unsafe.
 */
public class Unsafe {

  /**
   * The unsafe.
   */
  public static final @NotNull sun.misc.Unsafe unsafe;

  private static final Method ensureClassInitialized; // unsafe.ensureClassInitialized
  private static final Object lookupInstance; // MethodHandles.lookup()
  private static final Method ensureInitialized; // MethodHandles.lookup().ensureInitialized(klass);

  static {
    // http://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/src/share/classes/sun/misc/Unsafe.java
    try {
      Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      // mmap = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
      // unmap = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
      f.setAccessible(true);
      unsafe = (sun.misc.Unsafe) f.get(null);

      Method _ensureClassInitialized = null;
      Method _ensureInitialized = null;
      Object _lookupInstance = null;
      try {
        // Note: Before Java 15, `MethodHandles.lookup().ensureInitialized(klass)` does not exist, we need to
        // use Unsafe!
        Method ensureClassInitialized1 = sun.misc.Unsafe.class.getMethod("ensureClassInitialized", Class.class);
        _ensureInitialized = null;
        _lookupInstance = null;
      } catch (NoSuchMethodException ignore) {
        // In Java 23+ this method does not exist!
        _ensureClassInitialized = null;
        final Method lookup = MethodHandles.class.getMethod("lookup");
        _lookupInstance = lookup.invoke(null);
        _ensureInitialized = _lookupInstance.getClass().getMethod("ensureInitialized", Class.class);
      }
      ensureClassInitialized = _ensureClassInitialized;
      ensureInitialized = _ensureInitialized;
      lookupInstance = _lookupInstance;
    } catch (Exception e) {
      throw new InternalError(e);
    }
  }

  public static void ensureClassInitialized(Class<?> clazz) {
    try {
      if (ensureInitialized != null && lookupInstance != null) {
        ensureInitialized.invoke(lookupInstance, clazz);
        // == MethodHandles.lookup().ensureInitialized(klass.java);
      } else {
        ensureClassInitialized.invoke(unsafe, clazz);
        // == unsafe.ensureClassInitialized(klass.java)
      }
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  /**
   * Returns the offset of the field with the given name.
   * @param objectClass the class to query.
   * @param fieldName the name of the field to query.
   * @return the offset.
   */
  public static long fieldOffset(@NotNull Class<?> objectClass, @NotNull String fieldName) {
    final Field field;
    try {
      field = objectClass.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      throw new Error("No such field: " + objectClass.getName() + "::" + fieldName, e);
    }
    return unsafe.objectFieldOffset(field);
  }
}
