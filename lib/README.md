# Bundled runtime libraries

These JARs are packaged by `buildPlugin` so the plugin can be built offline.

- Jackson 2.19.2: JSON parsing, Apache License 2.0.
- CommonMark Java 0.30.0 and its GFM tables extension: Markdown parsing and HTML
  rendering, BSD 2-Clause License. Upstream: https://github.com/commonmark/commonmark-java
- Apache PDFBox 3.0.5 with FontBox, PDFBox IO and Commons Logging: in-plugin PDF
  text extraction, Apache License 2.0. Upstream: https://pdfbox.apache.org
- Apache POI 5.4.1 and XMLBeans: DOCX/PPTX/XLSX and legacy Office parsing,
  Apache License 2.0. Upstream: https://poi.apache.org
- Tess4J 5.15.0, Leptonica/JNA bindings and Tesseract 5.5 native libraries:
  offline image OCR. Tess4J and Tesseract are Apache License 2.0; Leptonica is
  BSD-2-Clause. Upstream: https://tess4j.sourceforge.net and
  https://github.com/tesseract-ocr/tesseract
- Tesseract `eng`, `chi_sim` and `osd` training data: Apache License 2.0.
- JSoup 1.18.3: HTML structure and table text extraction, MIT License.
  Upstream: https://jsoup.org

CommonMark artifacts are downloaded from Maven Central under `org.commonmark`.
The JARs include the upstream license in `META-INF/LICENSE.txt`.
PDFBox artifacts are downloaded from Maven Central under `org.apache.pdfbox` and
`commons-logging`. Their upstream license files are included in the JARs.
The bundled Tesseract native library currently supports Windows x86/x64. macOS
and Linux can use native Tesseract support in a later platform-specific package.
