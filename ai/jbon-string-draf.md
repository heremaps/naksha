# JBON2 string encoding and interactive decoding proposal

Status: **Draft for later implementation** — 2026-11-24. This records the agreed design, expected tradeoffs, and questions for a prototype. It does not change the current specification or wire format. JBON2 is not yet released; compatibility with the current draft encoding is not required. No legacy decoding or migration is part of this proposal.

## Why change it?

The current [JBON2 specification](../docs/latest/JBON2.md#string) uses `10xxxxxx` for a byte-sized string body containing variable-width code points and inline string references. `JbEncoder2` emits code points in one to three bytes, not as fixed 32-bit values. Its dictionary path can write a word into the buffer, decode those bytes for lookup, and then overwrite them with a reference. `JbDecoder2` can already skip a string from its encoded byte size, but materializes decoded strings with a `StringBuilder` and eagerly loads local dictionary strings. The old hash-bearing UTF-16 structure is specified but is not handled by the current JBON2 decoder.

The goal is to make strings cheap to **inspect without materializing**, while retaining useful compression for repeated values and shared prefixes such as MOM identifiers. Literal encoding should also be simpler. Full-write and full-decode speedups are hypotheses, not established results.

## Wire format decisions

### Literal strings

| Lead-in | Encoding | Payload | Length in Java `char`s |
| --- | --- | --- | ---: |
| `11ss_1110` | ISO-8859-1 | One unsigned byte per code unit `U+0000`–`U+00FF`. This is not Windows-1252. | `payloadBytes` |
| `11ss_1111` | UTF-16BE | Two bytes per UTF-16 code unit, big-endian, no BOM. | `payloadBytes / 2` |

- A *character* in this proposal means a Java `char` (UTF-16 code unit), not a Unicode code point or a displayed symbol. Java and JavaScript string indexing use this unit. A supplementary code point such as `😀` uses two code units and four UTF-16BE payload bytes.
- Standardize the size field for **all structures** as the **payload byte size**, excluding the lead-in and size field. This matches `JbEncoder2.writeStructHeader()` and `JbDecoder2.unitSize()`; several current specification tables instead say total size. `ss=00` means zero payload and no size field, and `01`, `10`, `11` mean unsigned 1-, 2-, and 4-byte big-endian payload sizes, respectively. Total wire size is header size plus payload size.
- Payload follows the header directly. Neither literal stores a hash. Reject odd UTF-16BE payload sizes. Prefer the shortest size field for ordinary literal emission.
- Both empty literal forms (`0xCE`, `0xCF`) are valid. Ordinarily encode the empty string as `0x80`. Within a book string table, use an empty literal instead, because table entries must be literal forms.
- The format limit is **`2^30 − 8` UTF-16 code units** for any string, including a string expanded from references. The corresponding UTF-16BE payload limit is **`2^31 − 16` bytes**. An implementation may support a smaller limit; real inputs above `2^20` code units are expected to be rare. Check lengths at operation boundaries (including normalized output and aggregate expansion), not per character. Check payload bounds and aggregate sizes without overflowing before reading or allocating.

### String references

The old `10` string-body encoding becomes empty/single/combined references. These denote string values, not ordinary references into a book's element table.

| Lead-in | Meaning | Bytes following lead-in |
| --- | --- | --- |
| `1000_0000` (`0x80`) | Empty string | None |
| `1000_0001`–`1000_0011` | Illegal | — |
| `1000_01bb` | Single reference into book `bb` | One byte, unsigned 8-bit index |
| `1000_10bb` | Single reference into book `bb` | Two bytes, unsigned 16-bit index |
| `1000_11bb` | Single reference into book `bb` | Four bytes, unsigned 30-bit index; top two bits must be zero |
| `1001_ssss` | Combined references | `ssss + 1` one-byte entries (2 book bits + 6 index bits each) |
| `1010_ssss` | Combined references | `ssss + 1` two-byte entries (2 book bits + 14 index bits each) |
| `1011_ssss` | Combined references | `ssss + 1` four-byte entries (2 book bits + 30 index bits each) |

All multibyte fields are big-endian. `bb`: `00` local, `01` members, `10` global, `11` const. Combined entries put `bb` in their high two bits and use the same width for every entry. The count is biased by one, so there are **1–16 entries**. Append their literal values in order with no implicit separator. A combined unit can be skipped using `1 + count × width`, without resolving any references. Its maximum wire size is **65 bytes**.

- All four books may expose string tables. A members book need not have one when its storage representation does not provide it. Normal member-value relocation and string lookup remain separate. The string index space is distinct from the ordinary element index space; do not assume member names automatically constitute the wire string table.
- String tables accept only literal ISO-8859-1 or UTF-16BE entries. They never contain references, so string reference depth is one and cycles are not possible. Reject malformed or out-of-range references when parsed or resolved, as appropriate; do not silently substitute empty strings for missing entries. Empty literal entries are valid, but the encoder should normally omit empty fragments.
- No inline literal fragments or old append-character flags. A suffix or separator needs a table entry if referenced. Constant-book separators are an option; any such table needs a specified, stable index assignment before use on the wire.

### Normalization and canonical logical bytes

- Encode strings in **NFC**, including keys and book strings. Decode valid data assuming this invariant. Provide `writeString(value, nfcNormalize = true)` or equivalent: `false` is a trusted caller assertion that the input already meets the NFC contract, not an instruction to store non-NFC data. It need not trigger a redundant verification pass.
- For Java/JavaScript round-trip compatibility, preserve unpaired surrogate code units **unchanged** and normalize the well-formed spans on either side independently. Do not repair them to `U+FFFD`, reject them, or split a valid surrogate pair between reference fragments. Verify equivalent behavior on JVM and JS rather than relying on a charset conversion that may replace input.
- Normalize the complete value **before** choosing fragments. Separately NFC-normalized parts need not concatenate to NFC (for example `A` + `U+030A`). The encoder must emit a composition of references only when their concatenation is the exact normalized sequence. Reject duplicate object keys after normalization rather than dropping or overwriting values.
- Canonical **logical bytes** always use lead-in `0xFF`, a fixed four-byte big-endian payload size, and the complete normalized UTF-16BE payload, without a hash. This includes the canonical empty value (`FF 00 00 00 00`). Literal width, book selection and fragmentation do not alter logical bytes. Physical encodings can still differ.
- Java string hashing is over unsigned UTF-16 code-unit values in order, `h = 31 × h + codeUnit` with wrapping 32-bit arithmetic. It is not the same operation as hashing the canonical JBON logical bytes. Neither `0` nor `-1` is available as an unknown Java hash sentinel.

## Encoder proposal: half-greedy matching

Use the platform NFC normalizer first, with an ASCII or trusted-input fast path where appropriate. Then traverse the normalized code units to determine literal width and discover references, and emit the selected representation. This is a goal of few traversals, not a guarantee of exactly two physical passes: normalization may buffer and reorder combining sequences, and exact hit verification and emission may revisit code units. Do not hash characters that normalization can still change.

Maintain two running Java hashes: a **prefix hash** from the beginning of the string, and a **fragment hash** from the end of the last accepted match. At an eligible boundary, check the prefix candidate first. An exact longer prefix match replaces all accepted fragments and resets the fragment start. Otherwise, an exact fragment match is accepted greedily and resets the fragment start, but the prefix hash continues. A full-string match can replace the whole prior plan. For `foo:bar:wall-e-id:4711`, the plan can progress from `[foo]` to `[foo, :bar]` to `[foo:bar]`, and ultimately to a single whole-string reference if it exists. This is intentionally not an optimal segmentation algorithm.

- Probe available books by hash, then verify length and exact code-unit content. Do not allocate a substring on every probe or treat a collision as a hit. For ties, prefer the cheaper encoding, with a stable book preference if needed. Avoid redundant probes when both candidate ranges are identical.
- Reject very short candidates early. Choose references only if their full incremental encoding cost beats the literal: include all entry widths, the possible widening of a combined unit, and any newly stored local literal and its framing. No hard threshold or book tie order is fixed by the wire format.
- If a selected reference plan leaves an unmatched suffix, include it as one local literal. Try to fit the plan with existing references before introducing a new entry. If the fragment count would exceed the limit, use at most 15 matched fragments plus one local literal for the whole remainder, or fall back to the full literal. Keep checking the prefix even after reaching the fragment cap so a later prefix/full match can replace the plan. Defer local insertion until the plan is selected and cost-effective; do not leave unused entries from superseded plans or intern a wholly unmatched string merely to reference it once.
- Let callers disable fragment compression to avoid substring probes (whole-string lookup can still be used). Benchmark literal-only, whole-string lookup and half-greedy modes independently. A later optional `optimizeStrings` final-build pass could find reuse across local entries and re-encode; defer it.
- For literals, once width and length are known, write the final header and reserve a checked output span, then emit the payload directly. The aim is to remove code-point parsing during emission, repeated capacity checks and string-header compaction, not to change general buffer safety.

## Interactive decoder and stack

Use reusable, growable byte-array stack storage for navigation and temporary state. Keep offsets rather than views into the stack backing array so growth cannot invalidate internal pointers. For the initial prototype, avoid a full navigation rewrite: literals need no per-string frame; allocate a string frame on the reusable stack only when inspecting a reference value. Skipping and finding the next unit should not populate a string frame or touch a book.

Proposed string frame (implementation detail, not wire format):

| Bytes | Contents |
| ---: | --- |
| 12 | Three 32-bit cached aggregates: expanded payload byte size, UTF-16 length, Java hash |
| 8 per fragment | Packed 32-bit `(bookId << 30) \| index` plus a 32-bit cached length/encoding descriptor |

The references alone need at most 64 bytes; the complete frame is at most **140 bytes**, excluding navigation overhead. An empty value needs no fragment entries and can represent all three aggregates as zero. For unresolved fragments initialize the descriptor to `-1`. Once a literal of length `L` is resolved:

```text
descriptor = (L << 1) | (1 for ISO-8859-1, 0 for UTF-16BE)
length = descriptor >>> 1
payloadBytes = length if (descriptor & 1) == 1, otherwise descriptor
```

The descriptor's sign bit remains clear when resolved. It is distinct from the reference word, whose sign bit may be set. Aggregate byte-size can start at `-1` to indicate unknown; track hash readiness separately if length/size can be computed without hashing. The cached byte-size is the **sum of expanded literal payload bytes**. It is neither the encoded unit's wire size nor the canonical UTF-16 payload size (`2 × length`).

- For literals, address character `i` at `payloadOffset + i` or `payloadOffset + 2*i`. For combined values, resolve and cache only enough fragment lengths to locate it, skipping any empty fragments. `startsWith` and key comparisons walk code units across boundaries and stop at the first mismatch. Hashing and native `String` construction are separate operations and may traverse the whole value.
- A stack alone does not remove eager book decoding. Provide indexed literal access (backing view, payload offset, length and encoding, or equivalent indexed access). The local book can index entry offsets without allocating native strings. External heap books may adapt existing `String` entries. Keep native string materialization as a convenience, not a prerequisite for navigation.
- Specify view lifetime: a cursor/stack-backed view is transient and cannot outlive its frame or decoder mapping. Any escaping view must retain valid backing state or copy/materialize. No stale view may silently address a new decoder mapping.

## Expected benefits and costs

| Workload | Expected outcome |
| --- | --- |
| Skip unrelated strings | Little gain from the wire change alone: the old format already exposes a byte size. New references are likewise skippable without loading books. |
| Inspect keys or prefixes, read length or selected characters | **Strongest reason to proceed.** Direct literal indexing and lazy fragment resolution avoid decoding the rest or allocating a native string; unsuccessful comparisons can stop early. |
| Hash or fully decode | Still linear in expanded length. Fixed-width literals simplify the loop, but full materialization still allocates a native string and fragmented values require lookups. Measure including local book setup. |
| Encode | Simpler literal emission is promising. NFC and repeated book probes/content checks can offset or outweigh it. A fast literal or whole-string-only mode remains useful. |
| Raw wire and stored size | Short ASCII literals usually grow by one header byte, ISO-8859-1 characters such as `é` can shrink, and text mixing ASCII with non-Latin-1 code units can grow because the whole literal uses UTF-16BE. Supplementary code points use four payload bytes instead of the current three. Repeated strings and shared prefixes can benefit from references, but a one-use suffix or the loss of inline separators may hurt. Measure raw and GZIP-stored sizes separately. |

The current Tuple read path inflates a GZIP-compressed feature before decoding. Lazy access reduces work **after** inflation; it is not random access into a compressed blob.

## When we return to this

1. **Revise the spec first.** Update sizes consistently, string/primitive classification, logical bytes, book rules and examples. Reconcile the current local `Dictionary` payload with the specified `Book` string table; do not assume the current `IBook` string accessor or wire layout already supports lazy literal views. Leave JBON1 unchanged.
2. **Prototype literals and reference decoding with a lazy cursor/book view.** Implement direct inspection before rewriting the entire navigation stack. Validate all lead-in/range boundaries, malformed sizes and references, odd UTF-16BE lengths, empty forms, the canonical empty encoding, mixed-width fragments and 16-entry limits, overflow, normalized key collisions, paired and unpaired surrogates, and JVM/JS parity. Verify canonical bytes and Java hashes agree across literal and reference representations.
3. **Measure interactive and full operations separately.** Benchmark skipping versus seeking and comparing keys, `startsWith` (including early mismatches), `charAt` across boundaries, hashing, and full materialization. Report CPU and allocation for first and repeated access, with local-book setup and GZIP inflation measured separately. Include small objects, large strings, mixed Unicode and representative MOM IDs.
4. **Compare encoder policies rather than assuming a speedup.** Compare current and proposed codecs on equivalent normalized inputs, then measure default NFC end to end. Compare literal-only, whole-string and half-greedy modes for CPU, allocation, raw bytes and stored bytes. Include high-index books, short keys and strings where fragment matching finds little reuse. Use the results to choose sensible default policies and probe thresholds.

**Conclusion:** Adopt fixed-width literals and bounded string references while the format is still open. They enable a genuine interactive read improvement, especially lazy key and prefix comparison. Treat the full stack rewrite, the default fragment-compression policy, and any build-time string optimizer as separate decisions. Encoding speed, full decode speed, and wire-size gains remain workload-dependent until measured.
