package com.here.naksha.lib.core.lambdas;

@FunctionalInterface
public interface Fe3<Z, A, B, C> extends Fe {

  Z call(A a, B b, C c) throws Exception;
}
