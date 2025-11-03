package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {
  // new String(utf8, 0, utf8.length, StandardCharsets.UTF_8)
  private final JsonParser jp = JsonParser.threadLocal();

  private Object parse(@NotNull String json) {
    final var json_bytes = json.getBytes(StandardCharsets.UTF_8);
    return jp.parse(json_bytes);
  }

  /// This should pin the string "test", so that all instances must always be the same!
  static final String test_SINGLETON = StringUtil.pin("test");

  @Test
  public void test_unquoted_string() {
    Object r = parse("test");
    String s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse(" test bar ");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test bar", s);

    r = parse("test // comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse("/* test*/ test // comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse("/* test*/ test /* comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);
  }

  @Test
  public void test_quoted_string() {
    Object r = parse("'test'");
    String s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse(" \"test bar\" ");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test bar", s);

    r = parse("'test' // comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse("/* test*/ 'test' // comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse("/* test*/ \"\\x74\\u0065\\u{73}t\" /* comments should be ignored");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);

    r = parse("\"\\x74\\u0065\\u{73}t\"");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertSame(test_SINGLETON, s);
  }

  @Test
  public void test_boolean() {
    Object r = parse("true");
    Boolean b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertSame(Boolean.TRUE, b);

    r = parse("false");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertSame(Boolean.FALSE, b);

    r = parse(" false ");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertFalse(b);

    r = parse("true // comments should be ignored");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertSame(Boolean.TRUE, b);

    r = parse("/* test*/ true // comments should be ignored");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertSame(Boolean.TRUE, b);

    r = parse("/* test*/ false /* comments should be ignored");
    b = assertInstanceOf(Boolean.class, r);
    assertNotNull(b);
    assertSame(Boolean.FALSE, b);
  }

  @Test
  public void test_null() {
    Object r = parse("null");
    assertNull(r);

    r = parse(" null ");
    assertNull(r);

    r = parse("null // comments should be ignored");
    assertNull(r);

    r = parse("/* test*/ null // comments should be ignored");
    assertNull(r);

    r = parse("/* test*/ null /* comments should be ignored");
    assertNull(r);
  }

  @Test
  public void test_plain_long() {
    Object r = parse("0");
    Long l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(0L, l);

    r = parse("-0");
    l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(0L, l);

    r = parse("-1");
    l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(-1L, l);

    r = parse("15");
    l = assertInstanceOf(Long.class, r);
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

    r = parse("0.0");
    d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    assertEquals(0.0d, d);

    r = parse("-0.0");
    d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    // Warning: Do not call equals on boxed values, they compare binary, and -0.0 is binary different from 0.0 !
    assertNotEquals(0.0d, d.doubleValue());
    assertEquals(-0.0d, d.doubleValue());

    r = parse("-1.0");
    d = assertInstanceOf(Double.class, r);
    assertNotNull(d);
    assertEquals(-1.0d, d);

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

  @Test
  public void test_array() {
    Object r = parse("[]");
    JsonArray arr = assertInstanceOf(JsonArray.class, r);
    assertNotNull(arr);
    assertEquals(0, arr.size());

    r = parse("[1, 2]");
    arr = assertInstanceOf(JsonArray.class, r);
    assertNotNull(arr);
    assertEquals(2, arr.size());
    assertEquals(1L, arr.get(0));
    assertEquals(2L, arr.get(1));

    r = parse("[1,, test, true, false, 'bar\\n', \"test\"]");
    arr = assertInstanceOf(JsonArray.class, r);
    assertNotNull(arr);
    assertEquals(7, arr.size());
    assertEquals(1L, arr.get(0));
    assertEquals(Json.UNDEFINED, arr.get(1));
    assertSame(test_SINGLETON, arr.get(2));
    assertSame(Boolean.TRUE, arr.get(3));
    assertSame(Boolean.FALSE, arr.get(4));
    assertEquals("bar\n", arr.get(5));
    assertSame(test_SINGLETON, arr.get(6));
  }

  @Test
  public void test_map() {
    Object r = parse("{}");
    JsonMap map = assertInstanceOf(JsonMap.class, r);
    assertNotNull(map);
    assertEquals(0, map.size());

    r = parse("{a:1, b:2}");
    map = assertInstanceOf(JsonMap.class, r);
    assertNotNull(map);
    assertEquals(2, map.size());
    assertEquals(1L, map.get("a"));
    assertEquals(2L, map.get("b"));
  }

  @Test
  public void test_map_internals() {
    final Object r = parse("{'a':1,,'b':test, 'c':true, d:false, 'foo':'bar\\n', 'test':\"test\"}");
    final JsonMap map = assertInstanceOf(JsonMap.class, r);
    assertNotNull(map);
    assertEquals(6, map.size());

    // 'a':1
    assertEquals("a", map.entries[0]);
    assertEquals(1L, map.entries[1]);
    assertEquals(1L, map.get("a"));

    // 'b':test
    assertEquals("b", map.entries[2]);
    assertSame(test_SINGLETON, map.entries[3]);
    assertSame(test_SINGLETON, map.get("b"));

    // 'c':true
    assertEquals("c", map.entries[4]);
    assertSame(Boolean.TRUE, map.entries[5]);
    assertSame(Boolean.TRUE, map.get("c"));

    // d:false
    assertEquals("d", map.entries[6]);
    assertSame(Boolean.FALSE, map.entries[7]);
    assertSame(Boolean.FALSE, map.get("d"));

    // 'foo':'bar\n'
    assertEquals("foo", map.entries[8]);
    assertEquals("bar\n", map.entries[9]);
    assertEquals("bar\n", map.get("foo"));

    // 'test':"test"
    assertSame(test_SINGLETON, map.entries[10]);
    assertSame(test_SINGLETON, map.entries[11]);
    assertSame(test_SINGLETON, map.get("test"));
  }

  @Test
  public void test_map_deep() {
    final Object r = parse("{test:a, 'b':[1,2], c:[1,{test:test}]}");
    final JsonMap root = assertInstanceOf(JsonMap.class, r);
    assertNotNull(root);
    assertEquals(3, root.size());
    assertEquals("a", root.get("test"));

    Object r2 = root.get("b");
    JsonArray arr = assertInstanceOf(JsonArray.class, r2);
    assertEquals(2, arr.size());
    assertEquals(1L, arr.get(0));
    assertEquals(2L, arr.get(1));

    Object r3 = root.get("c");
    arr = assertInstanceOf(JsonArray.class, r3);
    assertEquals(2, arr.size());
    assertEquals(1L, arr.get(0));

    Object r4 = arr.get(1);
    JsonMap map = assertInstanceOf(JsonMap.class, r4);
    assertEquals(1, map.size());
    assertSame(test_SINGLETON, map.get("test"));
  }
}