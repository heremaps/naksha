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
