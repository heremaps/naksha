package naksha.base;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {
  private final JsonParser jp = JsonParser.instance.get();

  private Object parse(@NotNull String json) {
    final var json_bytes = json.getBytes(StandardCharsets.UTF_8);
    return jp.parse(json_bytes);
  }

  @Test
  public void plainString() {
    Object r = parse("test");
    String s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test", s);

    r = parse(" test bar ");
    s = assertInstanceOf(String.class, r);
    assertNotNull(s);
    assertEquals("test bar", s);
  }

  @Test
  public void plainLong() {
    Object r = parse("15");
    Long l = assertInstanceOf(Long.class, r);
    assertNotNull(l);
    assertEquals(15L, l);
  }

}