@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * An immutable representation of a platform specific 64-bit integer.
 */
@JsExport
@JsName("BigInt")
interface Int64

// TODO:
// - Add members (we can patch the prototype, when use this.valueOf() instead of just this!
// - In Kotlin, prefer infix extension methods that use the static methods in "N"
// - In JavaScript implementation, the member methods should use the static methods of "N"
// - In JavaScript the member methods normally should never get a hit,
//   we just need them to be safe for kotlin developers that miss the extension methods!
// - In Java we need the member methods, but here they are not a problem anyway
//
// e.g. (BigInt.prototype as any).toJSON = function () {
//  return this.toString();
//};
//
