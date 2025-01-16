package com.here.naksha.lib.handlers.internal;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.handlers.NakshaAdminCollection;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.storage.http.HttpStorage;
import com.here.naksha.storage.http.HttpStorageProperties;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static naksha.model.util.RequestHelper.createFeatureRequest;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntHandlerForStoragesTest {

  @Mock
  INaksha naksha;

  IntHandlerForStorages handler;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    handler = new IntHandlerForStorages(naksha);
  }

  @Test
  void shouldFailWhenHttpStorageBearsInvalidProperties(){
    // Given:
    adminStorageAlwaysSucceeds();

    // And:
    NakshaProperties notHttpStorageProperties = new NakshaProperties();

    // And:
    Storage httpStorage = httpStorage(notHttpStorageProperties);

    // And:
    WriteRequest writeStorageRequest = createFeatureRequest(NakshaAdminCollection.STORAGES, httpStorage);

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
    Storage httpStorage = httpStorage(httpStorageProperties);

    // And:
    WriteRequest writeStorageRequest = createFeatureRequest(NakshaAdminCollection.STORAGES, httpStorage);

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
                new HttpStorageProperties(validUrl, -1, validSocketTimeout, emptyMap())),
        arguments("Invalid connection timeout: 91, allowed values (sec): 0 - 30",
                new HttpStorageProperties(validUrl, 91, validSocketTimeout, emptyMap())),
        arguments("Invalid socket timeout: -1, allowed values (sec): 0 - 90",
                new HttpStorageProperties(validUrl, validConnectionTimeout, -1, emptyMap())),
        arguments("Invalid socket timeout: 91, allowed values (sec): 0 - 90",
                new HttpStorageProperties(validUrl, validConnectionTimeout, 91, emptyMap())),
        arguments("Invalid url: this_is_not_a_url",
            new HttpStorageProperties("this_is_not_a_url", validConnectionTimeout, validSocketTimeout, emptyMap())),
        arguments("Invalid url: ftp://cool.files.com/static/rfc959.txt",
            new HttpStorageProperties("ftp://cool.files.com/static/rfc959.txt", validConnectionTimeout, validSocketTimeout, emptyMap())),
        arguments("""
                  Invalid connection timeout: -1, allowed values (sec): 0 - 30
                  Invalid socket timeout: 91, allowed values (sec): 0 - 90
                  Invalid url: ftp://cool.files.com/static/rfc959.txt""",
                new HttpStorageProperties("ftp://cool.files.com/static/rfc959.txt", -1, 91, emptyMap()))
    );
  }

  private Storage httpStorage(NakshaProperties xyzProperties) {
    Storage httpStorage = new Storage();
    httpStorage.setClassName(HttpStorage.class.getName());
    httpStorage.setId("test-http-storage");
    httpStorage.setTitle("some title");
    httpStorage.setDescription("some desc");
    httpStorage.setProperties(xyzProperties);
    return httpStorage;
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

}