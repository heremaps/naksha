package com.here.naksha.storage.http;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResponseToResultTest {

  @Test
  void name() {

    }

    private HttpResponse httpResponseMock(Map map, String body){
    HttpHeaders httpHeaders = HttpHeaders.of(
            map, (__,___) -> true
    );

    HttpResponse httpResponse = mock(HttpResponse.class);
      when(httpResponse.headers()).thenReturn(httpHeaders);
      when(httpResponse.body()).thenReturn(body);
      return httpResponse;
    }
}