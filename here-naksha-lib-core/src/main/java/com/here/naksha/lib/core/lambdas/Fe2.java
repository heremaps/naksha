package com.here.naksha.lib.core.lambdas;

@FunctionalInterface
public interface Fe2<Z, A, B> extends Fe {

  Z call(A a, B b) throws Exception;
}
