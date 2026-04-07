package com.worddict.worddictutils;

import org.json.JSONObject;

public class DictionaryProperties {
    public String name = "Unknown";
    public String nameLocal = "";
    public String plan = "Free";
    public String owner = "Unknown";
    public String license = "Free / Open Source";
    public String description = "";
    public String descriptionLocal = "";

    public DictionaryProperties() {}

    public DictionaryProperties(JSONObject json) {
        this.name = json.optString("name", this.name);
        this.nameLocal = json.optString("name-local", this.nameLocal);
        this.plan = json.optString("plan", this.plan);
        this.owner = json.optString("owner", this.owner);
        this.license = json.optString("license", this.license);
        this.description = json.optString("description", this.description);
        this.descriptionLocal = json.optString("description-local", this.descriptionLocal);
    }

    /**
     * Створює JSONObject на основі поточних полів класу.
     */
    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("name-local", nameLocal);
        json.put("plan", plan);
        json.put("owner", owner);
        json.put("license", license);
        json.put("description", description);
        json.put("description-local", descriptionLocal);
        return json;
    }
}
