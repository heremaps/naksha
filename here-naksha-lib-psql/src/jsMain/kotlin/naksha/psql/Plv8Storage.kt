@file:Suppress("OPT_IN_USAGE")

package naksha.psql

// TODO: We need to extend PgSession and implement it!
//       By design, this object will be found as 'naksha.psql.Plv8Storage'.
//
// TODO: This storage need to be attached to the plv8 global object, when 'naksha_start_session'
//       is called, all other SQL functions should expect that there is there as:
//       'plv8.nakshaStorage'. The moment the 'naksha_end_session' is called, the object should
//       be removed from the global 'plv8' object.
//       Starting a new session should close the old object, and creating a new one.
//
// TODO: We need a wrapper for PgConnection, actually, we only have on connection, so every
//       Plv8Storage will only hand out this one connection, and require it to be closed, before
//       another one is given out!
//       The 'Plv8Connection' therefore is a very thin wrapper around 'plv8'. We need as well own
//       wrappers for the other missing parts, like 'PgPlan', and 'PgCursor', but they are designed
//       already like the default 'plv8' API, therefore it should be a simply object with just a few
//       wrapper functions.
//
@JsExport
class Plv8Storage {

}