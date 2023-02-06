package com.here.xyz.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to read a property from a specific environment variable.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface JsonFilename {

  /**
   * The default filename.
   *
   * @return default filename.
   */
  String value() default "";
}
