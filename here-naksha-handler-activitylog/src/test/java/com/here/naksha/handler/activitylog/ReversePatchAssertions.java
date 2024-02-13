package com.here.naksha.handler.activitylog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.handler.activitylog.ActivityLogReversePatch.ReverseOp;
import java.util.List;
import org.junit.jupiter.api.Assertions;

public class ReversePatchAssertions {
  private final ActivityLogReversePatch subject;

  private ReversePatchAssertions(ActivityLogReversePatch subject) {
    this.subject = subject;
  }

  static ReversePatchAssertions assertThat(ActivityLogReversePatch reversePatch){
    return new ReversePatchAssertions(reversePatch);
  }

  ReversePatchAssertions hasAddOpsCount(int expectedAddOps){
    Assertions.assertEquals(expectedAddOps, subject.add());
    return this;
  }

  ReversePatchAssertions hasReplaceOpsCount(int expectedReplaceOps){
    Assertions.assertEquals(expectedReplaceOps, subject.replace());
    return this;
  }

  ReversePatchAssertions hasRemoveOpsCount(int expectedRemoveOps){
    Assertions.assertEquals(expectedRemoveOps, subject.remove());
    return this;
  }

  ReversePatchAssertions hasReverseOp(ActivityLogReversePatch.ReverseOp reverseOp){
    assertTrue(subject.ops().contains(reverseOp), "Missing op: %s\nActual ops: %s".formatted(reverseOp, subject.ops()));
    return this;
  }

  ReversePatchAssertions hasReverseOps(ActivityLogReversePatch.ReverseOp... reverseOps){
    for(ReverseOp op: reverseOps){
      hasReverseOp(op);
    }
    return this;
  }
}
