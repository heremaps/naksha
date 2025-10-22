package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {
  // new String(utf8, 0, utf8.length, StandardCharsets.UTF_8)
  private final JsonParser jp = JsonParser.instance.get();

  private Object parse(@NotNull String json) {
    final var json_bytes = json.getBytes(StandardCharsets.UTF_8);
    return jp.parse(json_bytes);
  }

  @Test
  public void test_unquoted_string() {
    Object r = parse("test");
    String s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test", s);

    r = parse(" test bar ");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test bar", s);

    r = parse("test // comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test", s);

    r = parse("/* test*/ test // comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test", s);

    r = parse("/* test*/ test /* comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test", s);
  }

  @Test
  public void test_boolean() {
    Object r = parse("true");
    Boolean b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertTrue(b);

    r = parse("false");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertFalse(b);

    r = parse(" false ");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertFalse(b);

    r = parse("true // comments should be ignored");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertTrue(b);

    r = parse("/* test*/ true // comments should be ignored");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertTrue(b);

    r = parse("/* test*/ false /* comments should be ignored");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertFalse(b);
  }

  @Test
  public void test_plain_long() {
    Object r = parse("15");
    Long l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(15L, l);

    r = parse(" 15  ");
    l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(15L, l);

    r = parse(" 9223372036854775807  ");
    l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(9223372036854775807L, l);

    r = parse(" -9223372036854775808  ");
    l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(-9223372036854775808L, l);
  }

  @Test
  public void test_plain_double() {
    Object r = parse("15.0");
    Double d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    assertEquals(15.0d, d);

    r = parse(" 15.0e0  ");
    d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    assertEquals(15.0d, d);

    r = parse(" 4503599627370500.0  ");
    d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    assertEquals(4503599627370500d, d);

    r = parse(" -1.23e100  ");
    d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    assertEquals(-1.23e100d, d);
  }

}