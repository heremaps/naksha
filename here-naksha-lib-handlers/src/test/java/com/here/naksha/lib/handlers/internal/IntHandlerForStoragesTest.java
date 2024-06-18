package com.here.naksha.lib.handlers.internal;

import static com.here.naksha.lib.core.util.storage.RequestHelper.createFeatureRequest;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaAdminCollection;
import naksha.model.NakshaContext;
import com.here.naksha.lib.core.models.XyzError;
import naksha.geo.XyzProperties;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.models.storage.ErrorResult;
import naksha.model.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.SuccessResult;
import naksha.model.WriteRequest;
import com.here.naksha.lib.core.models.storage.WriteXyzFeatures;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import com.here.naksha.storage.http.HttpStorage;
import com.here.naksha.storage.http.HttpStorageProperties;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
    XyzProperties notHttpStorageProperties = new XyzProperties();

    // And:
    Storage httpStorage = httpStorage(notHttpStorageProperties);

    // And:
    WriteXyzFeatures writeStorageRequest = createFeatureRequest(NakshaAdminCollection.STORAGES, httpStorage);

    // When:
    Result result = handler.process(eventWith(writeStorageRequest));

    // Then:
    assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.ILLEGAL_ARGUMENT, ((ErrorResult) result).reason);
  }

  @ParameterizedTest
  @MethodSource("invalidHttpProperties")
  void shouldFailOnInvalidHttpStorageProperties(String errorMsg, HttpStorageProperties httpStorageProperties) {
    // Given:
    adminStorageAlwaysSucceeds();

    // And:
    Storage httpStorage = httpStorage(httpStorageProperties);

    // And:
    WriteXyzFeatures writeStorageRequest = createFeatureRequest(NakshaAdminCollection.STORAGES, httpStorage);

    // When:
    Result result = handler.process(eventWith(writeStorageRequest));

    // Then:
    assertInstanceOf(ErrorResult.class, result);
    assertEquals(XyzError.ILLEGAL_ARGUMENT, ((ErrorResult) result).reason);
    assertEquals(errorMsg, ((ErrorResult) result).message);
  }

  private static Stream<Arguments> invalidHttpProperties() {
    String validUrl = "http://some.address.com/path/to/resource?foo=bar&lorem=ipsum";
    long validSocketTimeout = 5L;
    long validConnectionTimeout = 30L;
    return Stream.of(
        arguments("Invalid connection timeout: -1, allowed values (sec): 0 - 30",
            new HttpStorageProperties(validUrl, -1L, validSocketTimeout, emptyMap())),
        arguments("Invalid connection timeout: 91, allowed values (sec): 0 - 30",
            new HttpStorageProperties(validUrl, 91L, validSocketTimeout, emptyMap())),
        arguments("Invalid socket timeout: -1, allowed values (sec): 0 - 90",
            new HttpStorageProperties(validUrl, validConnectionTimeout, -1L, emptyMap())),
        arguments("Invalid socket timeout: 91, allowed values (sec): 0 - 90",
            new HttpStorageProperties(validUrl, validConnectionTimeout, 91L, emptyMap())),
        arguments("Invalid url: this_is_not_a_url",
            new HttpStorageProperties("this_is_not_a_url", validConnectionTimeout, validSocketTimeout, emptyMap())),
        arguments("Invalid url: ftp://cool.files.com/static/rfc959.txt",
            new HttpStorageProperties("ftp://cool.files.com/static/rfc959.txt", validConnectionTimeout, validSocketTimeout, emptyMap())),
        arguments("""
                  Invalid connection timeout: -1, allowed values (sec): 0 - 30
                  Invalid socket timeout: 91, allowed values (sec): 0 - 90
                  Invalid url: ftp://cool.files.com/static/rfc959.txt""",
            new HttpStorageProperties("ftp://cool.files.com/static/rfc959.txt", -1L, 91L, emptyMap()))
    );
  }

  private Storage httpStorage(XyzProperties xyzProperties){
    Storage httpStorage = new Storage(HttpStorage.class, "test-http-storage");
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
    when(writeSession.execute(any(WriteRequest.class))).thenReturn(new SuccessResult());
    IStorage adminStorage = mock(IStorage.class);
    when(adminStorage.newWriteSession(any(NakshaContext.class), anyBoolean())).thenReturn(writeSession);
    when(naksha.getAdminStorage()).thenReturn(adminStorage);
  }

}