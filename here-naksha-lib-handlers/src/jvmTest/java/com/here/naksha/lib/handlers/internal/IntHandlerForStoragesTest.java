package com.here.naksha.lib.handlers.internal;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.storage.http.HttpStorage;
import com.here.naksha.storage.http.HttpStorageProperties;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.stream.Stream;

import static com.here.naksha.lib.core.HubInternalIdentifiers.STORAGES;
import static naksha.model.util.RequestHelper.createFeatureRequest;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntHandlerForStoragesTest {

  private static final String TEST_MAP_ID = "test_map_id";

  @Mock
  INaksha naksha;

  IntHandlerForStorageConfigs handler;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    when(naksha.getAdminMapId()).thenReturn(TEST_MAP_ID);
    handler = new IntHandlerForStorageConfigs(naksha);
  }

  @Test
  void shouldFailWhenHttpStorageBearsInvalidProperties(){
    // Given:
    adminStorageAlwaysSucceeds();

    // And:
    NakshaProperties notHttpStorageProperties = new NakshaProperties();

    // And:
    NakshaStorage httpStorageConfig = httpStorageConfig(notHttpStorageProperties);

    // And:
    WriteRequest writeStorageRequest = createFeatureRequest(TEST_MAP_ID, STORAGES, httpStorageConfig);

    // When:
    Response result = handler.process(eventWith(writeStorageRequest));

    // Then:
    assertInstanceOf(ErrorResponse.class, result);
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, ((ErrorResponse) result).getError().getCode());
  }

  @ParameterizedTest
  @MethodSource("invalidHttpProperties")
  void shouldFailOnInvalidHttpStorageProperties(String errorMsg, HttpStorageProperties httpStorageProperties) {
    // Given:
    adminStorageAlwaysSucceeds();

    // And:
    NakshaStorage httpStorageConfig = httpStorageConfig(httpStorageProperties);

    // And:
    WriteRequest writeStorageRequest = createFeatureRequest(TEST_MAP_ID, STORAGES, httpStorageConfig);

    // When:
    Response result = handler.process(eventWith(writeStorageRequest));

    // Then:
    assertInstanceOf(ErrorResponse.class, result);
    assertEquals(NakshaError.ILLEGAL_ARGUMENT, ((ErrorResponse) result).getError().getCode());
    assertEquals(errorMsg, ((ErrorResponse) result).getError().getMsg());
  }

  private static Stream<Arguments> invalidHttpProperties() {
    String validUrl = "http://some.address.com/path/to/resource?foo=bar&lorem=ipsum";
    int validSocketTimeout = 5;
    int validConnectionTimeout = 30;
    return Stream.of(
        arguments("Invalid connection timeout: -1, allowed values (sec): 0 - 30",
                createHttpStorageProperties(validUrl, -1, validSocketTimeout, emptyMap())),
        arguments("Invalid connection timeout: 91, allowed values (sec): 0 - 30",
                createHttpStorageProperties(validUrl, 91, validSocketTimeout, emptyMap())),
        arguments("Invalid socket timeout: -1, allowed values (sec): 0 - 90",
                createHttpStorageProperties(validUrl, validConnectionTimeout, -1, emptyMap())),
        arguments("Invalid socket timeout: 91, allowed values (sec): 0 - 90",
                createHttpStorageProperties(validUrl, validConnectionTimeout, 91, emptyMap())),
        arguments("Invalid url: this_is_not_a_url",
            createHttpStorageProperties("this_is_not_a_url", validConnectionTimeout, validSocketTimeout, emptyMap())),
        arguments("Invalid url: ftp://cool.files.com/static/rfc959.txt",
            createHttpStorageProperties("ftp://cool.files.com/static/rfc959.txt", validConnectionTimeout, validSocketTimeout, emptyMap())),
        arguments("""
                  Invalid connection timeout: -1, allowed values (sec): 0 - 30
                  Invalid socket timeout: 91, allowed values (sec): 0 - 90
                  Invalid url: ftp://cool.files.com/static/rfc959.txt""",
                createHttpStorageProperties("ftp://cool.files.com/static/rfc959.txt", -1, 91, emptyMap()))
    );
  }

  private NakshaStorage httpStorageConfig(NakshaProperties properties) {
    NakshaStorage httpStorageConfig = new NakshaStorage();
    httpStorageConfig.setClassName(HttpStorage.class.getName());
    httpStorageConfig.setId("test-http-storage");
    httpStorageConfig.setTitle("some title");
    httpStorageConfig.setDescription("some desc");
    httpStorageConfig.setProperties(properties);
    return httpStorageConfig;
  }

  private IEvent eventWith(Request request) {
    IEvent event = mock(IEvent.class);
    when(event.getRequest()).thenReturn(request);
    return event;
  }

  private void adminStorageAlwaysSucceeds() {
    IWriteSession writeSession = mock(IWriteSession.class);
    when(writeSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResponse());
    IStorage adminStorage = mock(IStorage.class);
    when(adminStorage.newWriteSession(any(SessionOptions.class))).thenReturn(writeSession);
    when(naksha.getAdminStorage()).thenReturn(adminStorage);
  }

  private static HttpStorageProperties createHttpStorageProperties(String url, Integer connectionTimeout, Integer socketTimeout, Map<String, String> headers) {
    HttpStorageProperties httpStorageProperties = new HttpStorageProperties();
    httpStorageProperties.setUrl(url);
    httpStorageProperties.setConnectTimeout(connectionTimeout);
    httpStorageProperties.setSocketTimeout(socketTimeout);
    httpStorageProperties.setHeaders(headers);
    return httpStorageProperties;
  }

}