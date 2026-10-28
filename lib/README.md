# Bundled runtime libraries

These JARs are packaged by `buildPlugin` so the plugin can be built offline.

- Jackson 2.19.2: JSON parsing, Apache License 2.0.
- CommonMark Java 0.30.0 and its GFM tables extension: Markdown parsing and HTML
  rendering, BSD 2-Clause License. Upstream: https://github.com/commonmark/commonmark-java

CommonMark artifacts are downloaded from Maven Central under `org.commonmark`.
The JARs include the upstream license in `META-INF/LICENSE.txt`.
