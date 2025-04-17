# README
The documentation of Naksha is kept in the `docs` folder. It's available as GitHub Pages online:

https://heremaps.github.io/naksha/

Or in the source code. The rendering is done using:

- [markdown-it](https://github.com/markdown-it/markdown-it)
- [mermaid](https://github.com/mermaid-js/mermaid)
  - [Syntax](https://mermaid.js.org/intro/syntax-reference.html)
- [remark](https://github.com/gnab/remark)
  - [Getting started](https://github.com/gnab/remark/wiki#getting-started)
  - [Download latest version](https://gnab.github.io/remark/downloads/remark-latest.min.js)

To locally view this documentation, run a simple web-server in the root of the project, for example:

```bash
  python -m http.server 8000
```

The open: http://localhost:8000/docs/index.html

When editing in IntelliJ, install [mermaid](https://plugins.jetbrains.com/plugin/20146-mermaid) plugin.

## Mermaid hints

### `classDiagram`
- `Map~Key~Value~` becomes `Map<Key,Value>`
  - For generics, can as well be part of types and return values.
- Escape class names with backticks: \`a.b\`
- Prefixes of properties/methods:
  - `+` or nothing = Public
  - `-` = Private
  - `#` = Protected
  - `~` = Package/Internal
- Postfixes of properties/methods:
  - `*` = Abstract
  - `$` = Static
- Relations (there are more, but those we use):
  - General syntax:
    - `idA [cardinality] {arrow} [cardinality] idB [: {label}]`
  - Arrows
    - `<|--` = Extends
    - `..|>` = Implements
    - `-->` = Associated to
    - `<|--|>` = Link two-way
    - `--` = Link _(strong, never `null`)_
    - `..` = Link doted _(weak, optionally `null`)_
  - Cardinalities
    - `"1"` = Only 1
    - `"0..1"` = Zero or One
    - `"1..*"` = One or more
    - `"*"` = Zero or more
- To add annotations, add them directly after the class
```
class Foo {
  <<interface>>
}
```

### `flowchart` _(`graph`)_
