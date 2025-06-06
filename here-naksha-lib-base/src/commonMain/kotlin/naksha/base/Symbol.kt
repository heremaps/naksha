@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Symbols represent namespaces to manage hidden members of [PlatformObject]'s, normally used to implement [duck-typing](https://en.wikipedia.org/wiki/Duck_typing).
 *
 * Every [PlatformObject] does have hidden members called [symbol members][SymbolMember], they are not serializable by definition. They differ from normal members _(aka fields)_ in that a [SymbolMember] can be created, modified, and deleted at runtime. [Proxies][Proxy] are [bound][Proxy.bind] to [platform objects][PlatformObject] using a [SymbolMember], which have a _symbol_ as key, and the [Proxy] as value. A _symbol_ is a primitive value and managed by the platform code via the [Symbols] singleton.
 *
 * To summarize, a [SymbolMember] is a hidden runtime member in a [platform object][PlatformObject], that can be created, modified, and destroyed at runtime, and that allows to attach hidden values at runtime to an [PlatformObject]. They are mostly used to efficiently access the [PlatformObject] via [duck-typing](https://en.wikipedia.org/wiki/Duck_typing).
 *
 * Therefore, every [PlatformObject] can have multiple types linked in parallel _(not requiring replacement of each other, destroying potential inner caching)_. This allows to view the same [PlatformObject] using different data-models, as long as these data-models link to different _symbols_.
 *
 * When a [PlatformObject] is serialized or cloned, the [SymbolMember] are ignored _(as they are internal hidden members)_. This actually simplifies serialization, cloning, deserialization, and to calculate differences, patches, and apply patches to objects, without knowing the exact type or data-model.
 * @see SymbolMember
 */
@JsExport
@JsName("Symbol")
interface Symbol
