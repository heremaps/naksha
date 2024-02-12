package com.here.naksha.handler.activitylog;

import java.util.ArrayList;
import java.util.List;

public record ActivityLogDiff(
    int add,
    int copy,
    int move,
    int remove,
    int replace,
    List<Op> ops
) {

  record Op(
      String name,
      String path,
      String value
  ){

  }

  static Builder builder(){
    return new Builder();
  }

  static class Builder {
    int add;
    int copy;
    int move;
    int remove;
    int replace;
    List<Op> ops;

    private Builder() {
    }

    ActivityLogDiff build(){
      return new ActivityLogDiff(add, copy, move, remove, replace, ops);
    }

    Builder add(int add){
      this.add = add;
      return this;
    }

    Builder copy(int copy){
      this.copy = copy;
      return this;
    }

    Builder move(int move){
      this.move = move;
      return this;
    }

    Builder remove(int remove){
      this.remove = remove;
      return this;
    }

    Builder replace(int replace){
      this.replace = replace;
      return this;
    }

    Builder op(Op op){
      if(ops == null){
        ops = new ArrayList<>();
      }
      ops.add(op);
      return this;
    }
  }
}
