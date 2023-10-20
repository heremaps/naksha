/**

 A VOLATILE function can do anything, including modifying the database. It can return different results on successive calls with the same
 arguments. The optimizer makes no assumptions about the behavior of such functions. A query using a volatile function will
 re-evaluate the function at every row where its value is needed.

 A STABLE function cannot modify the database and is guaranteed to return the same results given the same arguments for all rows within a
 single statement. This category allows the optimizer to optimize multiple calls of the function to a single call. In particular,
 it is safe to use an expression containing such a function in an index scan condition. (Since an index scan will evaluate the
 comparison value only once, not once at each row, it is not valid to use a VOLATILE function in an index scan condition.)

 An IMMUTABLE function cannot modify the database and is guaranteed to return the same results given the same arguments forever. This
 category allows the optimizer to pre-evaluate the function when a query calls it with constant arguments. For example, a
 query like SELECT ... WHERE x = 2 + 2 can be simplified on sight to SELECT ... WHERE x = 4, because the function underlying
 the integer addition operator is marked IMMUTABLE.

 Using PLv8:
 - https://plv8.github.io/
 - https://github.com/plv8/plv8u/blob/master/doc/plv8.md

 Using pg_hint_plan:
 - https://pg-hint-plan.readthedocs.io/en/latest/index.html
 - https://dev.to/yugabyte/build-a-postgresql-docker-image-with-pghintplan-and-pgstatstatements-46pa

 */

// This executed as part of naksha_start_session
if (!plv8.naksha) {
  plv8.session = {
    appId: null,
    author: null
  };
  plv8.naksha = {
    "storageId": "${storage_id}",
    "schema": "${schema}",
  };
  plv8.naksha.log = function (msg) {
    plv8.elog(INFO, msg);
  };
  // Should create, update, delete and so on collections
  plv8.naksha.write_collections = function () {
  };
  // Should create, update, delete and so on features in collections.
  plv8.naksha.write_features = function () {
  };
  // Called from the BEFORE trigger to fix the XYZ-namespace.
  plv8.naksha.fix_xyz_namespace = function () {
  };
  // Called from the AFTER trigger, to write the history records, delete table and more
  // Needs to check if the history partition exists
  plv8.naksha.write_history = function () {
  };
  ${naksha_plv8_alweber}
  ${naksha_plv8_pawel}
  plv8.naksha.log("Ready");
}