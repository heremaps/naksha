package com.here.naksha.handler.activitylog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.handler.activitylog.ReversePatch.PatchOp;

public class ReversePatchAssertions {
  private final ReversePatch subject;

  private ReversePatchAssertions(ReversePatch subject) {
    this.subject = subject;
  }

  static ReversePatchAssertions assertThat(ReversePatch reversePatch){
    return new ReversePatchAssertions(reversePatch);
  }

  ReversePatchAssertions hasAddOpsCount(int expectedAddOps){
    assertEquals(expectedAddOps, subject.insert());
    return this;
  }

  ReversePatchAssertions hasUpdateOpsCount(int expectedUpdateOps){
    assertEquals(expectedUpdateOps, subject.update());
    return this;
  }

  ReversePatchAssertions hasRemoveOpsCount(int expectedRemoveOps){
    assertEquals(expectedRemoveOps, subject.remove());
    return this;
  }

  ReversePatchAssertions hasReverseOp(PatchOp reverseOp){
    assertTrue(subject.ops().contains(reverseOp), "Missing op: %s\nActual ops: %s".formatted(reverseOp, subject.ops()));
    return this;
  }

  ReversePatchAssertions hasReverseOps(PatchOp... reverseOps){
    for(PatchOp op: reverseOps){
      hasReverseOp(op);
    }
    return this;
  }
}
