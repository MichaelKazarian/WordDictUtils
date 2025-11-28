package com.worddict.worddictutils;

import java.util.List;
import java.util.ArrayList;

import com.worddict.worddictcore.Language;

public class MockBaseLanguage extends BaseLanguage {

    public MockBaseLanguage(Language language) {
        super(language);
    }

    @Override
    public List<Dictionary> listDictionaries() {
        return new ArrayList<Dictionary>();
    }

    @Override
    public void removeDictionary(Dictionary dictionary) {
        //
    };

    @Override
    public Dictionary createDictionary(Language targetLanguage) {
        return createDictionary(targetLanguage, "Mock dictionary name");
    };

    @Override
    public Dictionary createDictionary(Language targetLanguage, String name) {
        return null;
    };
}
