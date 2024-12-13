package com.here.naksha.lib.handlers.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Test;

class HandlerUtilTest {

  private final Random random = new Random();

  @Test
  void shouldCreateContextResult(){
    // Given


  }

  private List<NakshaFeature> randomFeatures(){
    int count = 1 + random.nextInt(10);
    List<NakshaFeature> list = new ArrayList<>(count);
    for(int i = 0; i < count; count ++){
      list.add()
    }
  }
}
