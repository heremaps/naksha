# Architecture Diagram
- Rename:
  - Add `IAny`
  - rename:
    - PObject -> `IMap` : `IAny` (on platform either Object or JvmMap<E> : Map<String,E>)
    - PArray -> `IArray` : `IAny` (on platform either Array or JvmArray<E> : List<E>)
    - PDataView -> `IBuffer` : `IAny` (on platform either DataView or JvmBuffer : ByteBuffer)
  - Add method to convert to/from buffer:
    - `Base.toByteArray(buffer: IBuffer): ByteArray`
    - `Base.toBuffer(bytes: ByteArray): IBuffer`
  - Rename classes to make it more clear, that we have proxies (prefix with `P`)
    - BaseType -> `abstract Proxy` (data: IAny
    - BaseObject -> `PObject` : Proxy
    - BaseArray<E> -> `PArray<E>` : Proxy
    - ~~BaseList~~
    - BaseMap -> `PMap<E>` : Proxy
    - ~~BasePair~~
    - BaseDataView -> `PBuffer` : Proxy
  - Split `Base.assign(object)` into
    - proxyObject(any: IAny, type: Klass<T : PObject>): T
    - proxyMap(map: IMap, componentType: Klass<E>): PMap<E>
    - proxyArray(array: IArray, componentType: Klass<E>): PArray<E>
    - Examples:
      - var x: PNakshaFeature = proxyObject(map, PNakshaFeature.klass)
      - var x: PSomeBinary = proxyObject(buffer, PSomeBinary.klass)
      - var x: PLineString = proxyObject(array, PLineString.klass)
      - var x: PArray<String> = proxyArray(array, Base.stringKlass)
      - var x: PMap<String> = proxyMap(o, Base.stringKlass)
  - We introduce a helper method to auto-generate the klass, so a new object simplifies to:
    - ```
      @JsExport
      open class Foo : PObject() {
        companion object {
          @JvmStatic
          val klass = Base.getKlass(Foo::class)
        }
        override fun klass(): BaseKlass<*> = klass
      }
      ```
  - All proxies should only accept one argument (defaults to _null_), which must be `IAny`
    - Construction of proxy is always: `PNakshaFeature()`
    - For concrete constructors, static factory methods are to be created, e.g.:
      - `PNakshaFeature.create("foo")`
      - `PPoint.create(5, 5)`
      - `PMom2Feature.create("foo", ....)`
  - **We only use the new base classes for features, URM and ARM**
- We want to have these libraries (with some special statics)
  - ! `lib-base`: Proxy classes and multi-platform base code
    - Jvm.fromBase(any: IAny, type: Class<T>) : T
    - Jvm.toBase(pojo: Object): IAny
    - Base.fromJson(json: String): IAny
    - Base.toJson(any: IAny): String
  - ! `lib-geo`: GeoJSON proxies
  - ! `lib-storage`: Storage classes like `ReadFeatures`, `WriteFeatures` aso. (include some proxies for example `PStorageCollection`)
    - Storage.fromBase(map: IMap) : StorageRow
    - Storage.toBase(row: StorageRow): IMap
  - ! `lib-jbon`: JBON serialization and deserialization
    - Jbon.fromBase(any: IAny): ByteArray
    - Jbon.toBase(bytes: ByteArray): IAny
  - ! `lib-plv8`: Implementation of `lib-storage` interfaces against Plv8 or JDBC
  - `lib-psql`: Pure Java library that uses `lib-plv8` and implements parallel execution of storage requests to improve performance.
  - `lib-mom2`: Include MOM v2.x and add helpers
    - Mom2.fromBase(any: IAny): MomFeature
    - Mom2.toBase(feature: MomFeature): IAny
  - ! `lib-diff`: Methods to calculate diffs and create patches on `IAny`
  - ! `lib-auth`: Authorization code


- Request
  - allowParallelExecution: Boolean = false
    - just implemented in `lib-psql`
    - we can execute the write-operations parallel
    - disadvantage: when doing commit, a partial execution can happen!
- ReadFeatures
  - addCollection
- WriteFeatures
  - ~~collectionId: String~~
  - operation: List<WriteOp>
    - WriteOp(collection: String, op: String (INSERT, UPDATE, UPSERT, DELETE, PURGE), feature: IMap)

```
try (val session = storage.newWriteSession()) {
  var req = new WriteCollection();
  val col = new PNakshaCollection().withId("foo").with
  req.ops.add(new WriteOp(EWriteOp.UPSERT, col));
  
  val req = new WriteFeatures(collectionId: String = null);
  req.allowParallelExecution = false
  req.ops.add(new WriteOp(EWriteOp.INSERT, feature, collectionId));
    -> if feature is proxy = proxy.data
    -> if feature is IMap, use it
    -> if feature is a pojo, Jvm.toBase(pojo: Object): IAny
    -> if it is no IMap, throw exception
  val resp = session.executeWrite(req)
  resp.result = List<ReadRow>
  resp.result.get(0).getFeature(Violation.class) -> 
  session.commit()
}
```
