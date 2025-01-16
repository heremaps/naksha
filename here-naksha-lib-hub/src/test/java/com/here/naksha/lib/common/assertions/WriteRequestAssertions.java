package com.here.naksha.lib.common.assertions;

import static com.here.naksha.lib.common.assertions.WriteAssertions.assertThatWrite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.function.Consumer;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Assertions;

public class WriteRequestAssertions {

  private final WriteRequest subject;

  private WriteRequestAssertions(WriteRequest subject){
    this.subject = subject;
  }

  public static WriteRequestAssertions assertThatWriteRequest(WriteRequest subject){
    return new WriteRequestAssertions(subject);
  }

  public WriteRequestAssertions hasSingleWriteThat(Consumer<WriteAssertions> writeAssertion){
    assertNotNull(subject);
    List<Write> writes = subject.getWrites();
    assertEquals(1, writes.size());
    writeAssertion.accept(assertThatWrite(writes.get(0)));
    return this;
  }
}
