# Third-Party Notices

This file lists third-party source code that has been **vendored** (copied) into this
repository, together with its original license and copyright notice.

It does **not** cover ordinary declared dependencies — the libraries resolved by Gradle
(`build.gradle`) and npm (`apps/studio/package.json`) at build time. Those are distributed
by their own maintainers under their own licenses and are not redistributed here as source.

---

## rrd-utils

- **Upstream:** <https://github.com/RoessinghResearch/rrd-utils>
- **Version vendored from:** `nl.rrd:rrd-utils:3.0.3`
- **License:** MIT
- **Copyright:** Copyright (c) 2022 Roessingh Research and Development
- **Context:** [#102](https://github.com/dialoguebranch/platform/issues/102) — dropping the
  `rrd-utils` dependency so `dlb-core-java` is self-contained
  ([#133](https://github.com/dialoguebranch/platform/pull/133),
  [#135](https://github.com/dialoguebranch/platform/pull/135)).

### What was vendored

Into `packages/core` (`dlb-core-java`):

| Location | Origin | Notes |
|----------|--------|-------|
| `com.dialoguebranch.expression` (8 classes) | `nl.rrd.utils.expressions` | The `.dlb` `<<if>>` / `<<set>>` expression engine, repackaged, not rewritten. |
| `com.dialoguebranch.expression.types` (24 classes) | `nl.rrd.utils.expressions.types` | Expression AST node types, repackaged. |
| `com.dialoguebranch.io.LineColumnNumberReader` | `nl.rrd.utils.io.LineColumnNumberReader` | Line/column-tracking reader used by the tokenizer, verbatim. |
| `com.dialoguebranch.util.CurrentIterator` | `nl.rrd.utils.CurrentIterator` | Cursor-style iterator wrapper, verbatim. |
| `com.dialoguebranch.exception.ParseException` | `nl.rrd.utils.exception.ParseException` | Re-authored in house style; folded under `DialogueBranchException`. |
| `com.dialoguebranch.exception.LineNumberParseException` | `nl.rrd.utils.exception.LineNumberParseException` | Re-authored in house style; logic preserved. |
| `com.dialoguebranch.json.JsonMapper` | `nl.rrd.utils.json.JsonMapper` | Re-authored; a thin Jackson `ObjectMapper` wrapper. |

Every vendored file carries the original `Copyright (c) 2022 Roessingh Research and
Development` notice and a provenance line in its header, alongside the Fruit Tree Labs
copyright. Both projects use the MIT License, so the permission text is shared.

Classes that were written from scratch against the JDK rather than copied — notably the
StAX-based `com.dialoguebranch.execution.parser.ProjectMetaDataParser` and the DOM-based
`com.dialoguebranch.editing.writer.ProjectMetaDataWriter` — are original work and are not
covered by this notice.

### License text

```
MIT License

Copyright (c) 2022 Roessingh Research and Development

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
