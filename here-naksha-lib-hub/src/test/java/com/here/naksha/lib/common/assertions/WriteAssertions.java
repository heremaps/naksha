package com.here.naksha.lib.common.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import naksha.model.request.Write;
import naksha.model.request.WriteOp;

public class WriteAssertions {

  private final Write subject;

  private WriteAssertions(Write subject) {
    this.subject = subject;
  }

  public static WriteAssertions assertThatWrite(Write subject) {
    return new WriteAssertions(subject);
  }

  public WriteAssertions hasOp(WriteOp op) {
    assertEquals(op, subject.getOp());
    return this;
  }

  public WriteAssertions hasCollectionId(String collectionId) {
    assertEquals(collectionId, subject.getCollectionId());
    return this;
  }

  public WriteAssertions hasId(String id){
    assertEquals(id, subject.getId());
    return this;
  }
}
