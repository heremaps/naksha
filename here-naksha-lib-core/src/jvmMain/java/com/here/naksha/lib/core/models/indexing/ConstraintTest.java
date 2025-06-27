package com.here.naksha.lib.core.models.indexing;

import naksha.base.PlatformEnum;
import naksha.base.PlatformType;
import org.jetbrains.annotations.NotNull;

import static naksha.base.Platform.forClass;

/**
 * The condition to apply.
 */
public class ConstraintTest extends PlatformEnum {
  public static final PlatformType<ConstraintTest> TYPE = forClass(ConstraintTest.class);

  @Override
  public @NotNull PlatformType<? extends PlatformEnum> namespace() {
    return TYPE;
  }

  /**
   * If the property is not null; ignores {@link #getValue()}.
   */
  public static final ConstraintTest NOT_NULL = defIgnoreCase(TYPE, "NOT_NULL");

  /**
   * If the value of the property is unique; ignores {@link #getValue()}.
   */
  public static final ConstraintTest UNIQUE = defIgnoreCase(TYPE, "UNIQUE");

  /**
   * If the value of the property is greater than the {@link #getValue()}.
   */
  public static final ConstraintTest GT = defIgnoreCase(TYPE, "GT");

  /**
   * If the value of the property is greater than or equal to the {@link #getValue()}.
   */
  public static final ConstraintTest GTE = defIgnoreCase(TYPE, "GTE");

  /**
   * If the value of the property is equal to the {@link #getValue()}.
   */
  public static final ConstraintTest EQ = defIgnoreCase(TYPE, "EQ");

  /**
   * If the value of the property is less than the {@link #getValue()}.
   */
  public static final ConstraintTest LT = defIgnoreCase(TYPE, "LT");

  /**
   * If the value of the property is less than or equal to the {@link #getValue()}.
   */
  public static final ConstraintTest LTE = defIgnoreCase(TYPE, "LTE");

  /**
   * If the length of the property is more than or equal to the defined {@link #getValue()}.
   */
  public static final ConstraintTest MIN_LEN = defIgnoreCase(TYPE, "MIN_LEN");

  /**
   * If the length of the property is less than or equal to the defined {@link #getValue()}.
   */
  public static final ConstraintTest MAX_LEN = defIgnoreCase(TYPE, "MAX_LEN");

  /**
   * If the value matches the given regular expression, given in the {@link #getValue()}.
   */
  public static final ConstraintTest MATCHES = defIgnoreCase(TYPE, "MATCHES");
}
