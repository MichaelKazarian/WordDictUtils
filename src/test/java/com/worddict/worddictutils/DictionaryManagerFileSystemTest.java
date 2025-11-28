package com.worddict.worddictutils;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import com.worddict.worddictcore.Language;

public class DictionaryManagerFileSystemTest {

    private DictionaryManagerFileSystem manager;

    @Before
    public void setUp() {
        manager = DictionaryManagerFileSystem.getInstance();
        manager.resetForTests();
    }

    @Test
    public void testSingleton() {
        DictionaryManagerFileSystem m1 = DictionaryManagerFileSystem.getInstance();
        DictionaryManagerFileSystem m2 = DictionaryManagerFileSystem.getInstance();
        assertSame(m1, m2);
    }

    @Test
    public void testAddAndGetBaseLanguage() {
        Language l = Language.getLanguageByCode("en");
        BaseLanguage en = new MockBaseLanguage(l);
        manager.addBaseLanguage(en);

        BaseLanguage result = manager.getBaseLanguage("en");
        assertNotNull(result);
        assertEquals("en", result.getLanguage().getLanguageCode());
    }

    @Test
    public void testCaseInsensitiveLookup() {
        Language l = Language.getLanguageByCode("es");
        BaseLanguage es = new MockBaseLanguage(l);
        manager.addBaseLanguage(es);

        assertNotNull(manager.getBaseLanguage("ES"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddNullLanguage() {
        manager.addBaseLanguage(null);
    }

    @Test
    public void testGetDictionaryName() {
        Language en = Language.getLanguageByCode("en");
        BaseLanguage base = new MockBaseLanguage(en);
        assertEquals("en-uk", base.getDictionaryName("uk", null));
        assertEquals("en-uk", base.getDictionaryName("uk", ""));
        assertEquals("en-de-extra", base.getDictionaryName("de", "Extra"));
        assertEquals("en-fr-my-name", base.getDictionaryName("fr", " my name "));
        assertEquals("en-pl-my-dict", base.getDictionaryName("pl", "my*dict?"));
        assertEquals("en-it-a-b-c", base.getDictionaryName("it", "A--B--C"));
    }

    @Test
    public void testGetDictionaryNameUnicode() {
        Language en = Language.getLanguageByCode("en");
        BaseLanguage base = new MockBaseLanguage(en);

        assertEquals("en-uk", base.getDictionaryName("uk", "тест"));
        assertEquals("en-fr", base.getDictionaryName("fr", "éàöü"));
    }
}
