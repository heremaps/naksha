# Coding conventions

This document aims to establish some coding rules that we agreed to follow in scope of `naksha` and
its modules.

Motivation

- make APIs less confusing, so that when different people work on different modules, there is
  consistency in how things are arranged
- save time when coding - when in doubt, relate to this
- save time when reviewing - see part of the code that is not aligned with these rules? Just link to
  this :)
- readability & quality - if we had time to discuss something and write it down, there's probably
  some quality/readability-based reasoning behind it

Caveats

- this is not set in stone, approaches changes, wrong decisions happen - let this evolve
- this is work in progress - by no means this document addresses all coding aspects & some things
  might not have been discussed yet :)

## Accessors

Even though it's quite basic subject, it's not only about getters and setters, so it's worth to have
some ground rules set.
How do we read and manipulate properties of the class?

| method                         | desc                                                                                                                            |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| **P** getProperty()            | simple getter, returns property directly                                                                                        | 
| **void** setProperty(P value)  | simple setter, sets property and does not return anything                                                                       | 
| **P** useProperty()            | aka *getOrCreate* - if property is set then it's returned, otherwise it gets created (some empty/default instance) and returned | 
| **SELF** withProperty(P value) | *setProperty* + return self (builder-like)                                                                               | 
| **void** addItem(T item)       | for properties that are collections, adds/sets an element and does not return anything                                          | 
| **void** removeItem(T item)    | for properties that are collections, removes an element and does not return anything                                            |
| **SELF** withItem(T item)      | *addItem*  + return self (builder-like)                                                                                         | 
| **SELF** withoutItem(T item)   | *removeItem* +  return self (builder-like)                                                                                      | 

### Sample

Consider following example:

```java
public class Person {

  private String name;
  private List<String> hobbies;

  public Person(String name, List<String> hobbies) {
    this.name = name;
    this.hobbies = hobbies;
  }

  // regular getters & setters
  public String getName() {
    return name;
  }

  public List<String> getHobbies() {
    return hobbies;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setHobbies(List<String> hobbies) {
    this.hobbies = hobbies;
  }

  // collection manipulation
  public void addHobby(String hobby) {
    if (hobbies == null) {
      hobbies = new ArrayList<>();
    }
    hobbies.add(hobby);
  }

  public void removeHobby(String hobby) {
    if (hobbies != null) {
      hobbies.remove(hobby);
    }
  }

  // builder-like data manipulation - direct access
  public Person withName(String name) {
    setName(name);
    return this;
  }

  public Person withHobbies(List<String> hobbies) {
    setHobbies(hobbies);
    return this;
  }

  // builder-like data manipulation - delegated access (ie when working with collections)
  public Person withHobby(String hobby) {
    addHobby(hobby);
    return this;
  }

  public Person withoutHobby(String hobby) {
    removeHobby(hobby);
    return this;
  }
}
```

Case for useX:

```java
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
```

