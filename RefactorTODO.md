# Refactor TODO

Я створив систему словників і доступ до них через CLI та API. Ось можливості.

`http://localhost:8080/api/v1/q/en/uk/your` повертає список слів

```text
your
yours
yourself
```

`http://localhost:8080/api/v1/langs` повертає набір підтримуваних мов

```json
[{"code":"en", "dictionaries":2}]
```

`http://localhost:8080/api/v1/dicts/en` повертає набір словників для мови

```json
{
"source": "en",
"dictionaries": [
{
"owner": "Mykhailo Kazarian",
"wordsCount": 2582,
"license": "Creative Commons BY-SA",
"description-local": "Основний словник загальної лексики, що містить найбільш вживані слова та вирази.",
"name": "English-Ukrainian Main Dictionary",
"description": "Comprehensive general dictionary covering high-frequency vocabulary for daily communication.",
"name-local": "Англійсько-український словник",
"id": "uk",
"plan": "Free"
},
{
"owner": "Unknown",
"wordsCount": 2582,
"license": "Free / Open Source",
"description-local": "",
"name": "Unknown",
"description": "",
"name-local": "",
"id": "uk-min",
"plan": "Free"
}
]
}
```

`http://localhost:8080/api/v1/g/en/uk/your` повертає визначення слова

```json
{
"LAST_SCORE": 0,
"WORD_NOTE": "(POS: pron)",
"IS_PROBLEM": false,
"SAMPLE_LIST": [],
"PRONOUNCES": [{
"memo": "",
"ipa": "jɔːr"
}],
"IS_LEARNED": false,
"IS_STARRED": false,
"TRANSLATIONS": [
{
"note": "",
"translation": "твій",
"samples": ["Is this your book? (Це твоя книга)"]
},
{
"note": "",
"translation": "ваш",
"samples": ["Your team did well. (Ваша команда добре впоралася.)"]
}
],
"AUDIO_SAMPLES": [],
"MEANING_SCORE": 0,
"IS_LEARNING": false,
"MEANING_CHECK_DATE": "",
"UUID": 1763044480537
}
```

І POST метод `f`, що задуманий для того, щоб знайти ті слова, які є в запиті. Якщо їх нема, я буду аналізувати різницю між потрібно/є в наявності

```javascript
fetch('http://localhost:8080/api/v1/f/en/uk', {
method: 'POST',
headers: {
'Content-Type': 'application/json'
},
body: JSON.stringify(["apple", "you"]) // Ваші слова для пошуку
})
.then(response => response.json().then(data => ({ status: response.status, data })))
.then(res => console.log(Статус: ${res.status}, res.data))
.catch(err => console.error('Помилка:', err));
```

Який поверне Статус: 200  Array [ "apple", "you" ]

Чекай на наступне повідомлення

Є код wiktionary бота, над яким ми тут працювали

```java
package com.worddict.wiktionarybot;

import com.worddict.worddictcore.HttpRequest;
import com.worddict.worddictcore.AudioSample;
import com.worddict.worddictcore.HttpResponse;
import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Pronounce;
import com.worddict.worddictcore.Translation;
import com.worddict.worddictcore.Utils;

public abstract class Wiktionary {
    protected Wiktionary(String abbreviation);

    public static Wiktionary get(String langCode) {
        switch (langCode) {
            case "en" : return WiktionaryEnglish.newInstance();
            case "de" : return WiktionaryDeutsch.newInstance();
            case "fr" : return WiktionaryFrench.newInstance();
            case "es" : return WiktionarySpanish.newInstance();
            default: return null;
        }
    }
public static Wiktionary get(String langCode);

    public int search(String word);

    public String getLanguageSection(String article);

    public abstract Pronounce.TextPronounce[] getIPA();

    public abstract AudioSample[] getAudioSamples();

    public Translation[] getTranslation(String[] langCodes);

    public abstract String getLanguageSectionRegexp();

    public String[] getWordProposals(String word);

    public String[] getWordVariants(String word);

    public String[] lookup(String word);

    protected String getSearchUrl(String word);

    protected String getSearchUrl(String word, int limit);

    protected String getWikiTextUrl(String word);

    private String urlEncode(String value);

    protected void addWikiTextToCache(String word, String wikitext);

    protected String getWikiTextPage(String word);

    protected boolean languageEquals(String master, String another);

    public static String getMediaWikiToURL(String wikiUrl)
            throws UnsupportedEncodingException;

    public static String normalizeFileName(String name);

    AudioSample newASInstance(String audioFileName);

    public static Language[] getSearchLanguages();

    protected String loadWordArticle(String word);

    private HttpResponse getUrlWithRetry(String url)
            throws IOException, InterruptedException;
}
```

Я думаю створити єдиний інтерфейс з вибором провайдера данних, або моє API, або Wiktionary, але важливо замінити виклики Wiktionary єдиним інтерфейсом

У Wiktionary API є багато власної специфіки, як то `search()` `getWordProposals()` `lookup()` `loadWordArticle()` `getLanguageSection()` `getIPA()` `getAudioSamples()` `getTranslation()`

Вони формують той json який я вавів вище як приклад.

Тепер, Wiktionary має стати просто одним із провайдерів даних. Наша ціль визначитись з інтерфейсом загального провайдера данних.

Зараз у мене є проект WordDictCore. У ньому два пакети:

```java
package com.worddict.worddictcore;
```

базовий, у якому описані інтерфейси та реалізація Word Language, HttpRequest, HttpResponse

і

```java
package com.worddict.wiktionarybot;
```

з базовим класом Wiktionary та реалізаціями для різних мов. Я бачу архітектуру так:

є чотири рівні, і вони мають таку семантику

```text
BackendProvider
        │
        ├── Wiktionary Backend
        └── API Backend
                 │
                 ▼
          DictionaryProvider
                 │
                 ├── getLanguages()
                 ├── getDictionaries(source)
                 └── getDictionary(source, dictionaryId)
                          │
                          ▼
                     Dictionary
                          │
                          ├── suggest()
                          ├── findWords()
                          └── getWord()
                                   │
                                   ▼
                            WordDefinition
```

тобто, головний принцип: BackendProvider відповідає на питання «звідки беруться словникові знання?», DictionaryProvider — «які словники цього backend доступні для заданої base language?», Dictionary — «працюй із цим конкретним словником».

1. BackendProvider Представляє джерело/бекенд даних:

```text
BackendProvider
    DictionaryProvider wiktionary = getWiktionaryProvider();
    DictionaryProvider api = getApiProvider();
```

2. DictionaryProvider відповідає на питанння які мови взагалі доступні і які словники є для конкретної мови.

```java
public interface DictionaryProvider {
    Language[] getLanguages(); // en, es, fr, de
    Dictionary [] getDictionaries(String source); en -> uk, uk-min, de
    Dictionary getDictionary(String source, String dictionaryId);
}
```

3. Провайдер операцій зі словами, він прив'язаний до конкретного словника

```java
public interface Dictionary {
    String[] suggest(String query);
    String[] findWords(String[] words);
    WordDefinition getWord(String word);
}
```

4. Доменна модель `WordDefinition` заради якої все робиться

```java
public class WordDefinition {
    private String note;
    private Pronounce pronounce;
    private ArrayList<Translation> translations;
    private ArrayList<AudioSample> audioSamples;
}
```

Вона не знає нічого про те, звідки взялося визначення.

Тоді зміни виглядають так:

```text
com.worddict
├── core
│   ├── AudioSample.java
│   ├── Constants.java
│   ├── Dictionary.java
│   ├── DictionaryDescription.java
│   ├── DictionaryProvider.java
│   ├── JsonUtils.java
│   ├── Language.java
│   ├── Pronounce.java
│   ├── SamplesList.java
│   ├── Translation.java
│   ├── Utils.java
│   ├── Word.java
│   └── WordDefinition.java
│
├── net
│   ├── HttpRequest.java
│   └── HttpResponse.java
│
└── provider
    ├── BackendProvider.java
    ├── api
    │   └── ApiProvider.java
    └── wiktionary
        ├── Wiktionary.java
        ├── WiktionaryDeutsch.java
        ├── WiktionaryEnglish.java
        ├── WiktionaryFrench.java
        └── WiktionarySpanish.java
```
