package com.apexpdl.i18ntable.shared;

import java.io.Serializable;
import java.util.*;

/**
 * Shared data model for the Strings Editor.
 *
 * <p>This is the client/server shared DTO that maps exactly to strings.json on disk.
 * It's designed to be GWT-serializable (implements Serializable, uses only GWT-compatible
 * types) so it can be passed over GWT-RPC.
 *
 * <p>In the full App Inventor integration, this becomes part of the YoungAndroidProjectNode
 * serialization tree. Here it's standalone for the prototype.
 */
public class StringsModel implements Serializable {

  private static final long serialVersionUID = 1L;

  private int schemaVersion = 1;
  private String defaultLanguage = "en";
  private List<String> languages = new ArrayList<>();

  /**
   * strings.get(key).get(langCode) = translated value
   * Using LinkedHashMap to preserve insertion order for stable serialization.
   */
  private Map<String, Map<String, String>> strings = new LinkedHashMap<>();

  // GWT requires a no-arg constructor for serialization
  public StringsModel() {}

  public StringsModel(String defaultLanguage) {
    this.defaultLanguage = defaultLanguage;
    this.languages.add(defaultLanguage);
  }

  // ---- Language operations ----

  public List<String> getLanguages() {
    return Collections.unmodifiableList(languages);
  }

  public boolean addLanguage(String langCode) {
    if (langCode == null || langCode.trim().isEmpty()) return false;
    if (languages.contains(langCode)) return false;
    languages.add(langCode);
    return true;
  }

  public boolean removeLanguage(String langCode) {
    if (langCode.equals(defaultLanguage)) return false; // cannot remove default
    languages.remove(langCode);
    // Clean up translations for this language
    for (Map<String, String> translations : strings.values()) {
      translations.remove(langCode);
    }
    return true;
  }

  public String getDefaultLanguage() {
    return defaultLanguage;
  }

  // ---- Key operations ----

  public Set<String> getKeys() {
    return strings.keySet();
  }

  /**
   * Adds a new string key. Returns false if key already exists or is invalid.
   * Valid keys: [a-z][a-z0-9_]* (valid Java identifier, XML element name, Swift string key)
   */
  public boolean addKey(String key) {
    if (!isValidKey(key)) return false;
    if (strings.containsKey(key)) return false;
    strings.put(key, new LinkedHashMap<>());
    return true;
  }

  public boolean removeKey(String key) {
    return strings.remove(key) != null;
  }

  public boolean renameKey(String oldKey, String newKey) {
    if (!strings.containsKey(oldKey)) return false;
    if (!isValidKey(newKey)) return false;
    if (strings.containsKey(newKey)) return false;
    Map<String, String> translations = strings.remove(oldKey);
    strings.put(newKey, translations);
    return true;
  }

  // ---- Translation operations ----

  public String getTranslation(String key, String langCode) {
    Map<String, String> translations = strings.get(key);
    if (translations == null) return null;
    return translations.get(langCode);
  }

  /**
   * Gets the translation for a key, falling back to the default language if missing.
   */
  public String getTranslationWithFallback(String key, String langCode) {
    String value = getTranslation(key, langCode);
    if (value != null && !value.isEmpty()) return value;
    return getTranslation(key, defaultLanguage);
  }

  public void setTranslation(String key, String langCode, String value) {
    Map<String, String> translations = strings.get(key);
    if (translations == null) {
      translations = new LinkedHashMap<>();
      strings.put(key, translations);
    }
    if (value == null || value.isEmpty()) {
      translations.remove(langCode);
    } else {
      translations.put(langCode, value);
    }
  }

  // ---- Coverage ----

  /**
   * Returns the translation coverage percentage for a given language.
   * Coverage = (number of keys with a non-empty translation) / (total keys) * 100
   */
  public int getCoveragePercent(String langCode) {
    if (strings.isEmpty()) return 100;
    int translated = 0;
    for (Map<String, String> translations : strings.values()) {
      String val = translations.get(langCode);
      if (val != null && !val.isEmpty()) translated++;
    }
    return (translated * 100) / strings.size();
  }

  /**
   * Returns the set of keys that have no translation for the given language.
   */
  public Set<String> getUntranslatedKeys(String langCode) {
    Set<String> missing = new LinkedHashSet<>();
    for (Map.Entry<String, Map<String, String>> entry : strings.entrySet()) {
      String val = entry.getValue().get(langCode);
      if (val == null || val.isEmpty()) {
        missing.add(entry.getKey());
      }
    }
    return missing;
  }

  // ---- Serialization (JSON) ----

  /**
   * Serializes to the strings.json format.
   * In GWT client code, use JavaScriptObject / JSON overlay types.
   * This method is for the server-side / prototype use.
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"schema_version\": ").append(schemaVersion).append(",\n");
    sb.append("  \"default_language\": \"").append(escapeJson(defaultLanguage)).append("\",\n");
    sb.append("  \"languages\": [");
    for (int i = 0; i < languages.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append("\"").append(escapeJson(languages.get(i))).append("\"");
    }
    sb.append("],\n");
    sb.append("  \"strings\": {\n");
    Iterator<Map.Entry<String, Map<String, String>>> it = strings.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, Map<String, String>> entry = it.next();
      sb.append("    \"").append(escapeJson(entry.getKey())).append("\": {");
      Iterator<Map.Entry<String, String>> tIt = entry.getValue().entrySet().iterator();
      while (tIt.hasNext()) {
        Map.Entry<String, String> t = tIt.next();
        sb.append("\"").append(escapeJson(t.getKey())).append("\": \"")
          .append(escapeJson(t.getValue())).append("\"");
        if (tIt.hasNext()) sb.append(", ");
      }
      sb.append("}");
      if (it.hasNext()) sb.append(",");
      sb.append("\n");
    }
    sb.append("  }\n");
    sb.append("}");
    return sb.toString();
  }

  // ---- Validation ----

  /**
   * Valid key: starts with lowercase letter, followed by lowercase letters, digits, or underscores.
   * This ensures validity as: Java identifier, XML element name, Swift string key, Android resource name.
   */
  public static boolean isValidKey(String key) {
    if (key == null || key.isEmpty()) return false;
    if (!Character.isLowerCase(key.charAt(0))) return false;
    for (int i = 1; i < key.length(); i++) {
      char c = key.charAt(i);
      if (!Character.isLowerCase(c) && !Character.isDigit(c) && c != '_') return false;
    }
    return true;
  }

  // ---- Helpers ----

  private String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }

  public int getSchemaVersion() { return schemaVersion; }
  public Map<String, Map<String, String>> getStrings() { return strings; }
}
