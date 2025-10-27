# PDF Append Sign Demo – iText 5.5.6 branch

This repository hosts a nursing-record signing demo. The original codebase was
implemented with iText 7. This branch (`iText-5.5.6`) rebuilds the workflow on
top of iText 5.5.6 while keeping the command-line interface familiar.

The downgrade requires substantial API changes (iText 5 bundles form, layout,
and signing APIs into a single artifact), so the implementation focuses on the
core append-mode signing flows:

* generating a nursing-record template with AcroForm fields and signature
  widgets;
* appending row-level signatures that fill in the row fields and lock them;
* applying a visible "electronic" signature stamp; and
* verifying the resulting document along with optional DocMDP certification.

Additional utilities from the iText 7 branch (structure debugging, widget
repair, etc.) are not part of this edition because the legacy library lacks
feature parity. The module-level README (`pdf-incremental-sign-demo/README.md`)
spells out the missing features and the iText 5 modules that could exhibit
regressions.

## Repository layout

The project is a single Maven module located under
`pdf-incremental-sign-demo/`. The shaded JAR exposes the CLI entry point
`com.demo.App`.

```
pdf-incremental-sign-demo/
├── pom.xml                     # Maven build targeting iText 5.5.6
├── README.md                   # Detailed usage and downgrade notes
└── src/main/java/com/demo      # CLI, signing, and verification code
```

## Building the shaded CLI

```bash
cd pdf-incremental-sign-demo
mvn -q -DskipTests package
```

> **Note:** The execution above fails in the training sandbox because Maven
> Central blocks unauthenticated plugin downloads (HTTP 403). On a workstation
> with proper Maven access the command succeeds and produces
> `target/pdf-incremental-sign-demo-1.0-SNAPSHOT-jar-with-dependencies.jar`.

## Running the demo scenario

1. **Create the template**
   ```bash
   java -jar target/...jar create-template --out template.pdf --rows 6
   ```
2. **Sign a row**
   ```bash
   java -jar target/...jar sign-row --src template.pdf --dest revision2.pdf \
       --row 1 --time "08:00" --text "测量体温" --nurse "张护士"
   ```
3. **Append an electronic signature**
   ```bash
   java -jar target/...jar sign-electronic --src revision2.pdf --dest final.pdf
   ```
4. **Verify**
   ```bash
   java -jar target/...jar verify --pdf final.pdf
   ```

For DocMDP certification, provide the `--certP12` option to `create-template`
or run the standalone `certify` command. See the module README for further
examples, options, and downgrade caveats.
