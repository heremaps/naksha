//package com.here.xyz.util;
//
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertTrue;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//import java.util.HashMap;
//import java.util.Map;
//import javax.annotation.Nonnull;
//import org.jetbrains.annotations.Nullable;
//import org.junit.Test;
//
//public class EncryptionTest {
//  // us-east-1 KMS test key.
//  private static final String wikvaya_xyz_hub_test_key = "e56eaee3-01e6-4bed-8fc3-9da33c9b425b";
//  private static final String PASSWORD = "password";
//  private static final String PASSWORD_ENC = "{{AQICAHi8YAXsK0xTa3XeJ06ChRDRkkXxfGeLeViN7J/1bpw+HgFWWOhWQgl5DXd29btDrvflAAAAZjBkBgkqhkiG9w0BBwagVzBVAgEAMFAGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQM7Y5EdseYxh/ZnZBBAgEQgCMXJEeNclgd8ciyQ+vfgQPBjqKfv/toSnMIZB3bnUndcd2Ptw}}";
//
//  public static class FooConfig extends JsonConfigFile<FooConfig> {
//    protected FooConfig(@Nonnull String filename) {
//      super(filename);
//    }
//
//    @Override
//    public @Nullable String keyId() {
//      return wikvaya_xyz_hub_test_key;
//    }
//
//    @JsonProperty
//    @JsonEncrypt
//    public String secret;
//
//    @JsonProperty
//    public String plain;
//
//    // Should not be serialized, but can be restored!
//    public String bar;
//  }
//
//  @Test
//  public void test_encrypt() {
//    final FooConfig fooConfig = new FooConfig("foo.json");
//    fooConfig.secret = PASSWORD;
//    fooConfig.plain = "Hello World";
//    fooConfig.bar = "Great";
//
//    final Map<String, Object> map = fooConfig.toMap();
//    Object raw;
//    String value;
//
//    assertEquals(2, map.size());
//    assertTrue(map.containsKey("secret"));
//    assertTrue(map.containsKey("plain"));
//    assertFalse(map.containsKey("bar"));
//
//    raw = map.get("secret");
//    assertNotNull(raw);
//    assertTrue(raw instanceof String);
//    value = (String) raw;
//    assertTrue(value.startsWith(ConfigCrypt.encPrefix));
//    assertTrue(value.endsWith(ConfigCrypt.encPostfix));
//
//    raw = map.get("plain");
//    assertNotNull(raw);
//    assertTrue(raw instanceof String);
//    value = (String) raw;
//    assertFalse(value.startsWith(ConfigCrypt.encPrefix));
//    assertFalse(value.endsWith(ConfigCrypt.encPostfix));
//  }
//
//  @Test
//  public void test_decrypt() {
//    final FooConfig fooConfig = new FooConfig("foo.json");
//    final Map<String,Object> values = new HashMap<>();
//    values.put("secret", PASSWORD_ENC);
//    values.put("plain", "Hello World");
//    values.put("bar", "Great");
//    fooConfig.load(values);
//
//    assertEquals(PASSWORD, fooConfig.secret);
//    assertEquals("Hello World", fooConfig.plain);
//    assertEquals("Great", fooConfig.bar);
//  }
//}