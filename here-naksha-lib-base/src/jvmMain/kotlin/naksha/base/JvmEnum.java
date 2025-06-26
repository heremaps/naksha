package naksha.base;

/**
 * If an enumeration class should be created from pure Java code, it must extend from this class, instead of from {@link JsEnum}. This solves a compiler issue with Kotlin not generating the correct overloading functions for `length` and/or `charAt`.
 * @since 3.0
 */
public abstract class JvmEnum extends JsEnum {

  @Override
  public int length() {
    return super.getLength();
  }

  @Override
  public char charAt(int index) {
    return super.get(index);
  }
}
