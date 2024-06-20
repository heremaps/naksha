package com.here.naksha.lib.auth;

import static java.util.Collections.emptyList;

import java.util.List;

public class Employee extends Person {

  public static final String DEFAULT_MANAGER_NAME = "Scott";
  private Person manager;

  public Employee(String name) {
    super(name, List.of("making_money"));
  }

  // use == get or create
  Person useManager() {
    if (manager == null) {
      setManager(new Person(DEFAULT_MANAGER_NAME, emptyList()));
    }
    return manager;
  }

  public Person getManager() {
    return manager;
  }

  public void setManager(Person manager) {
    this.manager = manager;
  }
}
