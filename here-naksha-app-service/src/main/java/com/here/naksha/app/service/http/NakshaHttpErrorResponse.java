package com.here.naksha.app.service.http;

import naksha.model.NakshaError;
import naksha.model.request.ErrorResponse;
import org.jetbrains.annotations.NotNull;

public class NakshaHttpErrorResponse extends ErrorResponse {

  private static final String STREAM_ID_KEY = "streamId";

  public static NakshaHttpErrorResponse httpErrorResponse(@NotNull NakshaError error, @NotNull String streamId){
    NakshaHttpErrorResponse errorResponse = new NakshaHttpErrorResponse();
    errorResponse.setError(error);
    errorResponse.setStreamId(streamId);
    return errorResponse;
  }

  public void setStreamId(@NotNull String streamId){
    setRaw(STREAM_ID_KEY, streamId);
  }

  public String getStreamId(){
    return (String) getRaw(STREAM_ID_KEY);
  }
}
