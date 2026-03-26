# gwt-i18n-table

A working GWT prototype of the **Strings Editor** for MIT App Inventor's internationalization support ([Issue #3710](https://github.com/mit-cml/appinventor-sources/issues/3710)).

Built as part of my GSoC 2026 proposal. The goal was to get real GWT CellTable code written and tested before the proposal — not just describe what I'd build.

## What It Demonstrates

The core challenge in building the Strings Editor for App Inventor is: **how do you make an editable two-dimensional table (keys × languages) scale to 10+ languages without horizontal scroll destroying usability?**

ewpatton raised this directly in the issue thread. I built three approaches and tested them:

| Approach | Verdict |
|---|---|
| Full table (all languages as columns) | Breaks at 4+ languages |
| **Language sidebar + single-language view** | **Scales to 12+ languages ✅** |
| Key-centric expansion panel | Awkward for bulk editing |

This prototype implements **Option B** (sidebar + single-language view).

## Features

- **Language sidebar** — select a language to edit; shows coverage %; add/remove languages
- **CellTable with inline editing** — click any translation cell to edit inline (EditTextCell)
- **Reference column** — default language always visible alongside the editing column
- **Translation coverage indicator** — "67% translated for Nepali"
- **Untranslated key highlighting** — rows with missing translations get a yellow background
- **Real-time key validation** — enforces `[a-z][a-z0-9_]*` (valid for Java, XML, Swift, Android)
- **JSON serialization** — `strings.json` format auto-updates in a preview pane on every edit
- **Add/remove keys and languages** — full CRUD on the data model

## Running Locally

Requires Java 11+ and Maven 3.8+.

```bash
# Clone the repo
git clone https://github.com/apexpdl/gwt-i18n-table
cd gwt-i18n-table

# Compile and run in dev mode
mvn gwt:devmode

# Or compile to JavaScript
mvn gwt:compile

# Open in browser
open http://localhost:8888/index.html
```

## Architecture

```
src/main/java/com/apexpdl/i18ntable/
├── I18nTable.gwt.xml              # GWT module descriptor
├── client/
│   └── I18nTableApp.java          # Entry point — all UI code
└── shared/
    └── StringsModel.java          # Data model (GWT-serializable, server/client shared)
```

### StringsModel

The data model maps directly to `strings.json`:

```java
StringsModel model = new StringsModel("en");  // "en" is the default language
model.addLanguage("ne");
model.addKey("greeting");
model.setTranslation("greeting", "en", "Hello!");
model.setTranslation("greeting", "ne", "नमस्ते!");

System.out.println(model.getCoveragePercent("ne")); // 100
System.out.println(model.toJson());
// {
//   "schema_version": 1,
//   "default_language": "en",
//   "languages": ["en", "ne"],
//   "strings": {
//     "greeting": {"en": "Hello!", "ne": "नमस्ते!"}
//   }
// }
```

### Key Validation

All keys must match `[a-z][a-z0-9_]*`:

```java
StringsModel.isValidKey("greeting")        // true
StringsModel.isValidKey("cancel_button")   // true
StringsModel.isValidKey("Cancel")          // false — uppercase
StringsModel.isValidKey("1st_screen")      // false — starts with digit
StringsModel.isValidKey("sensor pitch")    // false — space
```

This regex ensures keys are valid simultaneously as:
- Java identifiers (for `R.string.key_name`)
- XML element names (for `<string name="key_name">`)
- Swift string keys (for `NSLocalizedString("key_name", ...)`)
- Android resource names

## Connection to App Inventor

In the full implementation, `StringsModel` becomes the client-side representation of `strings.json` inside the `.aia` archive. It's held in a `StringsManager` singleton and registered with `EditorManager` as a new editor type (following the pattern used by the IoT and Alexa skill editors).

The `I18nTableApp` UI becomes `StringsEditor.java` in:
```
appinventor/appengine/src/com/google/appinventor/client/editor/StringsEditor.java
```

Key differences in the full integration:
- Uses App Inventor's `BlocklyPanel` styling and UiBinder templates
- Integrates with `YoungAndroidProjectService` for save/load
- Notifies the Designer when string keys change (for `@string/` property references)
- Populates the blocks editor key dropdown via `StringsManager`

## Patterns Learned

Building this prototype taught me the hard parts:

1. **`EditTextCell` focus management** — Keyboard navigation between editable cells requires custom `KeyUpHandler` logic. GWT's default tab behavior doesn't do what you want in a CellTable.

2. **`ListDataProvider` + `setRowStyles`** — Combining a data provider with dynamic row styles requires calling `dataProvider.refresh()` after model changes, *not* just updating the list.

3. **`SingleSelectionModel` for row actions** — Getting the currently selected row for "Remove Key" required a `SingleSelectionModel` attached to the `CellTable`, not just click handlers on cells.

4. **GWT's `isValidKey` regex** — GWT doesn't support `String.matches()` cleanly in client code (regex support is limited). The hand-written character-by-character validator in `StringsModel.isValidKey()` is the correct GWT-compatible approach.

## Author

Apex Poudel — [github.com/apexpdl](https://github.com/apexpdl) — GSoC 2026 applicant, MIT App Inventor
