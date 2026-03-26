package com.apexpdl.i18ntable.client;

import com.apexpdl.i18ntable.shared.StringsModel;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.cellview.client.*;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.view.client.*;

import java.util.*;

/**
 * GWT prototype for the App Inventor Strings Editor.
 *
 * <p>Architecture:
 * - Left sidebar: list of languages, add/remove controls, coverage indicator
 * - Right panel: editable CellTable with columns [Key | Default Language (ref) | Selected Language]
 * - Bottom: add key input, import/export CSV buttons
 *
 * <p>This prototype demonstrates:
 * 1. Dynamic GWT CellTable with inline EditTextCell editing
 * 2. Language sidebar with selection and coverage display
 * 3. Real-time validation of key names ([a-z][a-z0-9_]*)
 * 4. Untranslated key highlighting (yellow background)
 * 5. JSON serialization/deserialization
 * 6. Translation coverage percentage
 *
 * <p>This directly maps to the StringsEditor class that will be built in
 * appinventor/appengine/src/com/google/appinventor/client/editor/StringsEditor.java
 */
public class I18nTableApp implements EntryPoint {

  private StringsModel model;
  private String selectedLanguage;

  // UI components that need to update on model changes
  private ListBox languageListBox;
  private CellTable<StringRow> cellTable;
  private ListDataProvider<StringRow> dataProvider;
  private Label coverageLabel;
  private Label warningLabel;
  private TextBox newKeyInput;
  private TextArea jsonOutputArea;

  @Override
  public void onModuleLoad() {
    // Initialize with a sample model matching our health app use case
    model = new StringsModel("en");
    model.addLanguage("es");
    model.addLanguage("ne");
    model.addLanguage("tam");

    // Seed with example data
    model.addKey("greeting");
    model.setTranslation("greeting", "en", "Hello!");
    model.setTranslation("greeting", "es", "¡Hola!");
    model.setTranslation("greeting", "ne", "नमस्ते!");
    model.setTranslation("greeting", "tam", "खुरुम्बा सेबा!");

    model.addKey("cancel");
    model.setTranslation("cancel", "en", "Cancel");
    model.setTranslation("cancel", "es", "Cancelar");
    model.setTranslation("cancel", "ne", "रद्द");
    // Intentionally missing Tamang to demo untranslated highlighting

    model.addKey("prenatal_reminder");
    model.setTranslation("prenatal_reminder", "en", "Your next checkup is on {date}");
    model.setTranslation("prenatal_reminder", "ne", "तपाईंको अर्को जाँच {date} मा छ");
    // Missing es and tam to demo coverage

    selectedLanguage = "ne"; // Start with Nepali selected

    buildUI();
  }

  private void buildUI() {
    // ---- Root layout: horizontal split ----
    DockLayoutPanel root = new DockLayoutPanel(Unit.PX);
    root.setSize("100%", "100vh");

    // ---- Header ----
    HTML header = new HTML(
      "<div style='background:#1a73e8;color:white;padding:12px 20px;font-size:18px;" +
      "font-weight:bold;font-family:Roboto,sans-serif'>" +
      "App Inventor — Strings Editor (i18n Prototype)" +
      "<span style='font-size:12px;font-weight:normal;margin-left:16px;opacity:0.8'>" +
      "gwt-i18n-table by Apex Poudel — GSoC 2026 Proposal</span>" +
      "</div>"
    );
    root.addNorth(header, 48);

    // ---- Status bar (JSON preview toggle) ----
    HTML statusBar = new HTML(
      "<div style='background:#f1f3f4;border-top:1px solid #ddd;padding:6px 16px;" +
      "font-size:12px;color:#666;font-family:monospace'>" +
      "strings.json preview (auto-updates on edit)</div>"
    );
    jsonOutputArea = new TextArea();
    jsonOutputArea.setSize("100%", "120px");
    jsonOutputArea.setReadOnly(true);
    jsonOutputArea.getElement().getStyle().setProperty("fontFamily", "monospace");
    jsonOutputArea.getElement().getStyle().setProperty("fontSize", "11px");

    VerticalPanel bottomPanel = new VerticalPanel();
    bottomPanel.setWidth("100%");
    bottomPanel.add(statusBar);
    bottomPanel.add(jsonOutputArea);
    root.addSouth(bottomPanel, 150);

    // ---- Main area: sidebar + content ----
    DockLayoutPanel mainArea = new DockLayoutPanel(Unit.PX);
    root.add(mainArea);

    // ---- Left sidebar ----
    mainArea.addWest(buildSidebar(), 220);

    // ---- Right content ----
    mainArea.add(buildContentPanel());

    RootLayoutPanel.get().add(root);
    refreshAll();
  }

  private Widget buildSidebar() {
    VerticalPanel sidebar = new VerticalPanel();
    sidebar.setWidth("100%");
    sidebar.getElement().getStyle().setProperty("background", "#f8f9fa");
    sidebar.getElement().getStyle().setProperty("borderRight", "1px solid #ddd");
    sidebar.getElement().getStyle().setProperty("padding", "12px");
    sidebar.getElement().getStyle().setProperty("boxSizing", "border-box");

    HTML sidebarTitle = new HTML("<b style='font-size:13px;color:#333'>Languages</b>");
    sidebar.add(sidebarTitle);
    sidebar.setCellHeight(sidebarTitle, "30px");

    // Language list
    languageListBox = new ListBox();
    languageListBox.setSize("100%", "180px");
    languageListBox.addChangeHandler(e -> {
      int idx = languageListBox.getSelectedIndex();
      if (idx >= 0) {
        selectedLanguage = model.getLanguages().get(idx);
        refreshTable();
        refreshCoverage();
      }
    });
    sidebar.add(languageListBox);

    // Coverage label
    coverageLabel = new Label();
    coverageLabel.getElement().getStyle().setProperty("fontSize", "11px");
    coverageLabel.getElement().getStyle().setProperty("color", "#555");
    coverageLabel.getElement().getStyle().setProperty("marginTop", "4px");
    sidebar.add(coverageLabel);

    // Add language controls
    HorizontalPanel addLangPanel = new HorizontalPanel();
    addLangPanel.setWidth("100%");
    addLangPanel.getElement().getStyle().setProperty("marginTop", "8px");
    TextBox langInput = new TextBox();
    langInput.setWidth("90px");
    langInput.getElement().setAttribute("placeholder", "e.g. fr");
    Button addLangBtn = new Button("+ Add");
    addLangBtn.addClickHandler(e -> {
      String code = langInput.getValue().trim();
      if (model.addLanguage(code)) {
        langInput.setValue("");
        refreshSidebar();
        refreshAll();
      } else {
        Window.alert("Invalid or duplicate language code: " + code);
      }
    });
    addLangPanel.add(langInput);
    addLangPanel.add(addLangBtn);
    sidebar.add(addLangPanel);

    Button removeLangBtn = new Button("Remove selected");
    removeLangBtn.getElement().getStyle().setProperty("marginTop", "4px");
    removeLangBtn.getElement().getStyle().setProperty("fontSize", "11px");
    removeLangBtn.getElement().getStyle().setProperty("color", "#d32f2f");
    removeLangBtn.addClickHandler(e -> {
      if (selectedLanguage.equals(model.getDefaultLanguage())) {
        Window.alert("Cannot remove the default language (" + model.getDefaultLanguage() + ")");
        return;
      }
      if (Window.confirm("Remove language '" + selectedLanguage + "'?")) {
        model.removeLanguage(selectedLanguage);
        selectedLanguage = model.getDefaultLanguage();
        refreshSidebar();
        refreshAll();
      }
    });
    sidebar.add(removeLangBtn);

    // Separator
    HTML sep = new HTML("<hr style='border:none;border-top:1px solid #ddd;margin:12px 0'/>");
    sidebar.add(sep);

    // Import/Export
    HTML ioTitle = new HTML("<b style='font-size:12px;color:#333'>Import / Export</b>");
    sidebar.add(ioTitle);

    Button exportBtn = new Button("Export JSON");
    exportBtn.setWidth("100%");
    exportBtn.getElement().getStyle().setProperty("marginTop", "4px");
    exportBtn.addClickHandler(e -> {
      // In the prototype, just show in the textarea
      jsonOutputArea.setValue(model.toJson());
    });
    sidebar.add(exportBtn);

    // Note about CSV
    HTML csvNote = new HTML(
      "<div style='font-size:10px;color:#999;margin-top:6px'>" +
      "CSV import/export: implemented in full version.<br>" +
      "Lets teachers work offline with translators." +
      "</div>"
    );
    sidebar.add(csvNote);

    return sidebar;
  }

  private Widget buildContentPanel() {
    VerticalPanel content = new VerticalPanel();
    content.setWidth("100%");
    content.getElement().getStyle().setProperty("padding", "12px");
    content.getElement().getStyle().setProperty("boxSizing", "border-box");

    // Editing header
    HTML editingLabel = new HTML(
      "<div style='font-size:14px;font-weight:bold;color:#333;margin-bottom:8px'>" +
      "Editing: <span id='editing-lang-label'>" + selectedLanguage + "</span>" +
      "</div>"
    );
    content.add(editingLabel);

    // Warning label for untranslated keys
    warningLabel = new Label();
    warningLabel.getElement().getStyle().setProperty("color", "#e65100");
    warningLabel.getElement().getStyle().setProperty("fontSize", "12px");
    warningLabel.getElement().getStyle().setProperty("marginBottom", "8px");
    content.add(warningLabel);

    // ---- CellTable ----
    cellTable = new CellTable<>();
    cellTable.setWidth("100%");
    dataProvider = new ListDataProvider<>();
    dataProvider.addDataDisplay(cellTable);

    // Column 1: Key (read-only for now, rename via dialog in full version)
    TextColumn<StringRow> keyCol = new TextColumn<StringRow>() {
      @Override
      public String getValue(StringRow row) { return row.key; }
    };
    keyCol.setCellStyleNames("i18n-key-cell");
    cellTable.addColumn(keyCol, "Key");
    cellTable.setColumnWidth(keyCol, "200px");

    // Column 2: Default language (reference, read-only)
    TextColumn<StringRow> defaultCol = new TextColumn<StringRow>() {
      @Override
      public String getValue(StringRow row) {
        String val = model.getTranslation(row.key, model.getDefaultLanguage());
        return val != null ? val : "";
      }
    };
    cellTable.addColumn(defaultCol, model.getDefaultLanguage() + " (reference)");
    cellTable.setColumnWidth(defaultCol, "250px");

    // Column 3: Translation for selected language (editable)
    Column<StringRow, String> translationCol = new Column<StringRow, String>(new EditTextCell()) {
      @Override
      public String getValue(StringRow row) {
        String val = model.getTranslation(row.key, selectedLanguage);
        return val != null ? val : "";
      }
    };
    // Handle cell edits
    translationCol.setFieldUpdater((index, row, value) -> {
      model.setTranslation(row.key, selectedLanguage, value);
      dataProvider.refresh();
      refreshCoverage();
      refreshJsonPreview();
    });
    cellTable.addColumn(translationCol, selectedLanguage);
    cellTable.setColumnWidth(translationCol, "300px");

    // Row styles — highlight untranslated rows in yellow
    cellTable.setRowStyles((row, rowIndex) -> {
      String val = model.getTranslation(row.key, selectedLanguage);
      if (val == null || val.isEmpty()) {
        return "i18n-untranslated-row";
      }
      return "";
    });

    content.add(cellTable);

    // ---- Add key controls ----
    HorizontalPanel addKeyPanel = new HorizontalPanel();
    addKeyPanel.getElement().getStyle().setProperty("marginTop", "12px");
    addKeyPanel.setSpacing(6);

    newKeyInput = new TextBox();
    newKeyInput.setWidth("200px");
    newKeyInput.getElement().setAttribute("placeholder", "new_key_name");

    // Real-time validation feedback
    Label keyValidationLabel = new Label();
    keyValidationLabel.getElement().getStyle().setProperty("fontSize", "11px");
    keyValidationLabel.getElement().getStyle().setProperty("color", "#d32f2f");

    newKeyInput.addKeyUpHandler(e -> {
      String val = newKeyInput.getValue().trim();
      if (val.isEmpty()) {
        keyValidationLabel.setText("");
      } else if (!StringsModel.isValidKey(val)) {
        keyValidationLabel.setText(
          "⚠ Keys must match [a-z][a-z0-9_]* (lowercase, no spaces)"
        );
      } else if (model.getKeys().contains(val)) {
        keyValidationLabel.setText("⚠ Key already exists");
      } else {
        keyValidationLabel.setText("✓ Valid key");
        keyValidationLabel.getElement().getStyle().setProperty("color", "#2e7d32");
      }
    });

    Button addKeyBtn = new Button("+ Add Key");
    addKeyBtn.addClickHandler(e -> {
      String key = newKeyInput.getValue().trim();
      if (model.addKey(key)) {
        newKeyInput.setValue("");
        keyValidationLabel.setText("");
        refreshTable();
        refreshJsonPreview();
      } else {
        Window.alert(
          StringsModel.isValidKey(key)
            ? "Key already exists: " + key
            : "Invalid key name. Must match [a-z][a-z0-9_]*"
        );
      }
    });

    Button removeKeyBtn = new Button("Remove Selected Key");
    removeKeyBtn.getElement().getStyle().setProperty("color", "#d32f2f");
    removeKeyBtn.addClickHandler(e -> {
      SingleSelectionModel<StringRow> selModel =
        (SingleSelectionModel<StringRow>) cellTable.getSelectionModel();
      StringRow selected = selModel.getSelectedObject();
      if (selected == null) {
        Window.alert("Select a key row first");
        return;
      }
      if (Window.confirm("Remove key '" + selected.key + "'? This cannot be undone.")) {
        model.removeKey(selected.key);
        refreshTable();
        refreshCoverage();
        refreshJsonPreview();
      }
    });

    addKeyPanel.add(new Label("New key:"));
    addKeyPanel.add(newKeyInput);
    addKeyPanel.add(addKeyBtn);
    addKeyPanel.add(removeKeyBtn);

    content.add(addKeyPanel);
    content.add(keyValidationLabel);

    // Selection model for the table
    SingleSelectionModel<StringRow> selectionModel = new SingleSelectionModel<>();
    cellTable.setSelectionModel(selectionModel);

    return content;
  }

  // ---- Refresh methods ----

  private void refreshAll() {
    refreshSidebar();
    refreshTable();
    refreshCoverage();
    refreshJsonPreview();
  }

  private void refreshSidebar() {
    languageListBox.clear();
    int selectIdx = 0;
    List<String> langs = model.getLanguages();
    for (int i = 0; i < langs.size(); i++) {
      String lang = langs.get(i);
      String label = lang.equals(model.getDefaultLanguage()) ? lang + " (default)" : lang;
      languageListBox.addItem(label);
      if (lang.equals(selectedLanguage)) selectIdx = i;
    }
    languageListBox.setSelectedIndex(selectIdx);
  }

  private void refreshTable() {
    List<StringRow> rows = new ArrayList<>();
    for (String key : model.getKeys()) {
      rows.add(new StringRow(key));
    }
    dataProvider.getList().clear();
    dataProvider.getList().addAll(rows);
    dataProvider.refresh();
  }

  private void refreshCoverage() {
    int pct = model.getCoveragePercent(selectedLanguage);
    Set<String> missing = model.getUntranslatedKeys(selectedLanguage);
    coverageLabel.setText("Coverage: " + pct + "% for " + selectedLanguage);
    if (missing.isEmpty()) {
      warningLabel.setText("");
    } else {
      warningLabel.setText(
        "⚠ " + missing.size() + " untranslated key(s): " + String.join(", ", missing)
      );
    }
  }

  private void refreshJsonPreview() {
    jsonOutputArea.setValue(model.toJson());
  }

  // ---- Row data type ----

  static class StringRow {
    final String key;
    StringRow(String key) { this.key = key; }
  }
}
