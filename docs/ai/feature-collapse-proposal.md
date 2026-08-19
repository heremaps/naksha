  # Feature: Elimination of redundant feature-specific blob bytes

  This document proposes a persisted, immutable global book. The collection is used to build and select the book while writing; reading uses the book identified by the row and does not require the collection definition.


  ## Concepts

  | Concept | Meaning |
  |---|---|
  | `MemberLayout` | Collection-derived paths and slot mapping |
  | `mref` | Reference resolved from the current row's members book |
  | `gref` | Reference to an element in the global book |
  | `gbfn` | Feature number of the exact global book referenced by a row |

  ## Summary

  - Naksha already stores declared member values in row columns, but the `feature` blob repeats their keys and nested schema in every row.
  - Solution:
    - Generate a shared global book from the collection members and optional sample data.
    - Reserve global-book element 0 for a parameterized feature template whose variable leaves reference the current row's members book.
    - An exact template match stores a reference instead of the feature skeleton.
    - Each referencing row stores the feature number of the exact immutable book in `gbfn`.
    - Decoding only needs the row's members book and the global book identified by `gbfn`.
    - Books are immutable. Any changes publish a new book; historic rows continue to reference the old one.

  ## Problem and goal

  When a feature field is declared as a collection member, its value is already extracted into the row's members book. The problem is that the encoded `feature` still contains the object keys and overall field schema, even when every variable leaf is a member reference.

  The goal is to:

  - store the shared feature shape once;
  - reduce an exactly reconstructable feature blob to a small, around two-byte value;
  - preserve lossless and historical decoding;
  - avoid loading the current collection definition on the read path.

  ## Proposed architecture

  ```mermaid
  flowchart LR
    subgraph Publication
      C["Collection members<br/>+ optional samples"] --> L["MemberLayout"]
      L --> B["Global-book builder"]
      B --> S["Immutable book store"]
      S --> A["Collection active-book pointer"]
    end

    subgraph Write
      F["Feature"] --> E["Encoder"]
      L --> E
      A --> WR["Writer-side book resolver"]
      S --> WR
      WR --> E
      E --> ROW["Row: members + feature + gbfn"]
    end

    subgraph Read
      ROW --> R["Storage resolver / cache<br/>(database, gbfn)"]
      S --> R
      ROW --> D["Decoder"]
      R --> D
      D --> O["Reconstructed feature"]
    end
  ```

  Notably, the decode path does not need the collection. The collection is only used to create `MemberLayout`, generate the book and select the active book for writers. Storage resolves that book before passing it to the encoder or decoder. Readers use the database context and `gbfn` recorded on the row.

  ### Global-book content

  Element 0 is always the member feature template. For example:

  ```text
  { type: "Feature", id: m0, properties: { name: m1, var: m2 } }
  ```

  `m0`, `m1` and `m2` are placeholders. The same element is evaluated with a different members book for every row.

  Optional samples may contribute reusable objects to book entries. Participating member values are represented by their `mref` placeholders so sampled entries do not capture row-specific values. The builder processes samples as a bounded stream and retains only the derived book, not the sample objects.

  ### Component responsibilities

  | Component | Responsibility |
  |---|---|
  | Collection adapter | Compile collection members into a neutral `MemberLayout` |
  | Book/builder | Produce a deterministic, immutable and persistable global book |
  | Encoder | Match the feature against the template and emit either a global reference (if applicable) or normal encoding |
  | Decoder | Enter global elements and resolve nested member references in the current row context |
  | Book repository/resolver | Publish, load and cache immutable books by identity |
  | Collection lifecycle | Select the active book for writers and rotate to a new book without modifying previous ones |

  ## Encoding behavior

  1. Build the row's members book as it's done currently.
  2. Compare the feature's presence and structure with the feature template.
  3. If exact match, store the `gref` and the active book identity in `gbfn`.
  4. Else, use normal feature encoding.

  Given the example template:

  | Input shape | Collapse? |
  |---|---|
  | `{type, id, properties:{name, var}}` | yes |
  | `var` is missing | no |
  | extra `properties.note` | no |
  | `properties` is not an object | no |
  | invariant `type` differs | no |

  ## Decoding behavior

  For a row with `gbfn = null`, use the existing decode path. Otherwise:

  1. Resolve the immutable global book identified by database and `gbfn`.
  2. Enter the referenced global-book element.
  3. Resolve its nested `mref` values from the current row's members book.
  4. Reconstruct feature object.

  A referenced book that is missing, invalid or has the wrong identity is a read error; it is required in order to fully rebuild a feature.

  ## Book lifecycle and compatibility

  The proposed lifecycle persists every published book and treats it as immutable:

  ```mermaid
  flowchart TD
    P1["Publish immutable Book v1"] --> A1["Collection active book = v1"]
    A1 --> R1["Referencing rows record gbfn = v1"]
    A1 --> P2["Publish immutable Book v2"]
    P2 --> A2["Rotate active book to v2"]
    A2 --> R2["New referencing rows record gbfn = v2"]
    R1 -.-> P1
    R2 -.-> P2
  ```

  - Publishing the book and associating it with the collection must be atomic.
  - Writers use the current active book, but every row records the exact book it actually referenced at a time.
  - Old books remain available while stored rows may reference them.
  - `gbfn = null` is a valid entry that just expects no fully collapsed features

  ## Example

  ### Input feature:

  ```text
  { type: "Feature", id: "abc", properties: { name: "Abc", var: 750 } }
  ```

  ### Collection-derived template:

  ```text
  { type: "Feature", id: m0, properties: { name: m1, var: m2 } }
  ```

  ### What is stored:

  | Layer | Currently | Proposed |
  |---|---|---|
  | row members | `m0=abc, m1=Abc, m2=750` | unchanged |
  | `feature` blob | repeated keys, nesting and member references | a single reference |
  | `gbfn` | `null` | exact book feature number; database comes from the row context |
  | shared state | none | element 0 template stored once |

  ## Proposals for discussion

  | Proposal | Alternative | Trade-off |
  |---|---|---|
  | Persist every global book | Regenerate the member-only template from a immutable collection-schema version | Persistence adds book lifecycle management; regeneration moves historical-version responsibility onto collection schemas and deterministic template generation. |
  | At the start, limit collapse to guaranteed-present feature fields | Add explicit presence rules for required, conditional and storage-only members | The problematic scenario: if `properties.label` is optional, a feature without it may collapse, while a feature containing it uses normal encoding. |

  ## Delivery plan

  ### Task 1: immutable JBON2 Book and builder

  Deliver the standalone representation and generator:

  - spec-correct immutable Book model;
  - neutral `MemberLayout` input;
  - deterministic element-0 generation;
  - bounded streaming use of optional samples;
  - preservation of member references for later row-specific evaluation.

  This ticket has no collection or PostgreSQL dependency. Its outcome is a valid persisted-book artifact, not operational Naksha decoding.

  ### Task 2: codec, collection and storage integration

  Make the Book operational in Naksha:

  - collection-to-layout adaptation;
  - exact matching, collapsed encoding and normal-encoding fallback;
  - optional exact reuse of approved sample-derived entries;
  - decoding with the current row's members book;
  - book publication, resolution, caching and active-book rotation;
  - `gbfn = null` compatibility;
  - end-to-end storage and performance analysis - how much do we really gain.

  ## Benefits vs. risks

  Benefits:

  - ~2-byte `feature` blob for exact template matches;
  - no collection-definition lookup during decoding;
  - stable historical decoding through immutable books;
  - shared reuse of repeated structure and, optionally, exact sampled content.

  Costs and risks:
  - non-null `gbfn` and index overhead;
  - **requires book management, such as garbage collection, persistence and lookup;**
  - historic rows depend on continued availability of their books.

  ## JBON2 alignment

  JBON2 already describes the global-template plus members-book mechanism, but its scope rules currently conflict with member references inside a global element. Before implementation, the specification must explicitly allow such references when a members book is available and specify the new storage form/reference value.
