Я створив систему словників і доступ до них через CLI та API. Ось можливості.
http://localhost:8080/api/v1/q/en/uk/your повертає список слів

your
yours
yourself

http://localhost:8080/api/v1/langs повертає набір підтримуваних мов

[{"code":"en", "dictionaries":2}]

http://localhost:8080/api/v1/dicts/en повертає набір словників для мови
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
http://localhost:8080/api/v1/g/en/uk/your повертає визначення слова
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
І POST метод `f`, що задуманий для того, щоб знайти ті слова, які є в запиті. Якщо їх нема, я буду аналізувати різницю між потрібно/є в наявності
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
Який поверне Статус: 200  Array [ "apple", "you" ]

Чекай на наступне повідомлення
Є код wiktionary бота, над яким ми тут працювали
package com.worddict.wiktionarybot;

import com.worddict.worddictcore.HttpRequest;
import com.worddict.worddictcore.AudioSample;
import com.worddict.worddictcore.HttpResponse;
import com.worddict.worddictcore.Language;
import com.worddict.worddictcore.Pronounce;
import com.worddict.worddictcore.Translation;
import com.worddict.worddictcore.Utils;

public abstract class Wiktionary {
    final int MAX_HTTP_RETRIES = 2;
    private static final long HTTP_RETRY_WAIT_TIMEOUT_MS = 60_000L;

    protected String mAPIUrl;  //FROM Wiktionary.java
    protected HashMap<String, String> mCachedPages;

    protected Wiktionary(String abbreviation) {
        String url = "https://%s.wiktionary.org/w/api.php";
        this.mAPIUrl = String.format(url, abbreviation);
        mCachedPages = new HashMap<>();
    }

    public static Wiktionary get(String langCode) {
        switch (langCode) {
            case "en" : return WiktionaryEnglish.newInstance();
            case "de" : return WiktionaryDeutsch.newInstance();
            case "fr" : return WiktionaryFrench.newInstance();
            case "es" : return WiktionarySpanish.newInstance();
            default: return null;
        }
    }
    //========================================================================
    public int search(String word){
        mCachedPages = new HashMap<>();
        String [] pages = getWordVariants(word);
        String wikitext;
        for (String wordVariant: pages){
            wikitext = loadWordArticle(wordVariant);
            if (!wikitext.isEmpty()) {
                wikitext = getLanguageSection(wikitext);
                if (!wikitext.isEmpty())
                    addWikiTextToCache(wordVariant, wikitext);
            }
        }
        return mCachedPages.size();
    }
    //========================================================================
    /**
     * Wiktionary article can contains definitions for several languages
     * (e.g. https://en.wiktionary.org/wiki/test). This method tries to define
     * language section if it is possible.
     * @param article an article to search language section
     * @return Returns language section if possible; all article otherwise
     */
    public String getLanguageSection (String article){
        String res = article;
        //Each Language definition starts from ==Lang e.g. \n==english==\n
        //search from \n==english to next lang n==[^=] or end of string
        String regexp = getLanguageSectionRegexp();
        return Utils.searchRegexp(regexp, article);
    }
    //========================================================================
    /**
     * Search <a href="https://en.wikipedia.org/wiki/International_Phonetic_Alphabet">IPA</a>
     * definitions to search result.
     * @return Array contains <code>Pronounce.TextPronounce</code> instances if
     *         found; empty array otherwise.
     */
    public abstract Pronounce.TextPronounce [] getIPA();
    //========================================================================
    /**
     * Search audio samples to search result.
     * @return Array contains <code>AudioSample</code> instances if found;
     *         empty array otherwise.
     */
    public abstract AudioSample[] getAudioSamples();
    //========================================================================
    /**
     * Search translations to search result to target languages.
     * @param langCodes instance.
     * @return array with translations; empty array if never searched.
     */
    public Translation[] getTranslation(String [] langCodes) {
        LinkedHashSet<Translation> hs = new LinkedHashSet<>();
        for (String wikiText: mCachedPages.values()){
            for (String lng: langCodes) { //search translations;
                String [] tr = Utils.searchAllRegexp("\\|"+lng+"\\|(.+?)[}|]", wikiText, 1);
                for (String s: tr) hs.add(new Translation(s));
            }
        }
        return hs.toArray(new Translation[]{});
    }
    //========================================================================
    /**
     * @return returns regexp to retrieve language section from wiktionary
     * article.
     */
    public abstract String getLanguageSectionRegexp();

    public String [] getWordProposals(String word) {
        String url = getSearchUrl(word);
        try {
            //String candidate = Tools.toTitle(word); //better search
            HttpResponse response = getUrlWithRetry(url);
            JSONArray jsonBody = new JSONArray(response.getBody());
            JSONArray variants = jsonBody.getJSONArray(1);
            String[] result = new String[variants.length()];
            for (int i = 0; i < variants.length(); i++) {
                result[i] = variants.getString(i);
            }
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for retry");
        } catch (IOException | JSONException e) {
            System.err.printf(
                    "Failed to fetch URL: %s%n", url);
            e.printStackTrace(System.err);
        }
        return new String[0];
    }

    //========================================================================
    /**
     * FROM Wiktionary.java
     * Returns possible word variants for wiktionary. For more then 1 word
     * string " " will replace to "_".
     * For exsample:<br/>getWordVariants("test") returns ["test", "Test"];
     * <br/>getWordVariants("credit-deposit ratio") returns ["credit-deposit_ratio"]
     * @param word word.
     * @return Array with word variant. Empty array for empty string.
     *
     * credit-deposit_ratio, creditor's_rights,
     */
    public String [] getWordVariants(String word){
        String [] result = getWordProposals(word);
        Set<String> r = new HashSet<>();
        for (String variant: result) {
            if (languageEquals(word, variant))
                r.add(word.replace(" ", "_"));
        }
        result = r.toArray(new String[r.size()]);
        return result;
    }

    public String[] lookup(String word) {
        String url = getSearchUrl(word, 6);
        try {
            HttpResponse response = getUrlWithRetry(url);
            JSONArray jsonBody = new JSONArray(response.getBody());
            JSONArray variants = jsonBody.getJSONArray(1);
            if (variants.length() == 0) return new String[0];

            ArrayList<String> result = new ArrayList<>();
            for (int i = 0; i < variants.length(); i++) {
                result.add(variants.getString(i).replace(" ", "_"));
            }
            return result.toArray(new String[0]);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for retry");
        } catch (IOException | JSONException e) {
            System.err.printf(
                    "Failed to fetch URL: %s%n", url);
            e.printStackTrace(System.err);
        }
        return new String[0];
    }

    protected String getSearchUrl(String word) {
        return getSearchUrl(word, 10);
    }

    //========================================================================
    /**
     * Generates a Wiktionary search URL for a given word.
     *
     * @param word  The word to search for.
     * @param limit Maximum number of results to return.
     * @return A URL string for the Wiktionary "opensearch" API in JSON format.
     *
     * Example:
     * https://en.wiktionary.org/w/api.php?action=opensearch&search=test&namespace=0&limit=5&format=json&formatversion=2&suggest=true
     */
    protected String getSearchUrl(String word, int limit) {
        String params = String.join("&",
                "action=" + urlEncode("opensearch"),
                "search=" + urlEncode(word),
                "namespace=0",
                "limit=" + limit,
                "format=json",
                "formatversion=2",
                "suggest=true"
        );
        return mAPIUrl + "?" + params;
    }

    //========================================================================
    /**
     * Generates URL to fetch the raw wikitext of a Wiktionary article using the "parse" API.
     *
     * Example result:
     * https://en.wiktionary.org/w/api.php?action=parse&page=test&prop=wikitext&format=json
     *
     * @param word The word to fetch the article for.
     * @return Fully constructed URL to retrieve raw wikitext for the given word.
     */
    protected String getWikiTextUrl(String word) {
        String params = String.join("&",
                "action=" + urlEncode("parse"),
                "page=" + urlEncode(word),
                "prop=" + urlEncode("wikitext"),
                "format=" + urlEncode("json")
        );
        char separator = mAPIUrl.contains("?") ? '&' : '?';
        return mAPIUrl + separator + params;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    //========================================================================
    /**
     * Add wikitext content associated with word to cache.
     * @param word word
     * @param wikitext downloaded wikitext
     */
    protected void addWikiTextToCache(String word, String wikitext){
        mCachedPages.put(word, wikitext);
    }
    //========================================================================
    /**
     * Returns cached wikitext.
     * @param word searched word
     * @return wikitext associated with word. null if word not cached.
     */
    protected String getWikiTextPage(String word){
        return mCachedPages.get(word);
    }
    //========================================================================
    /**
     * Language dependent compare word.
     * @param master string to compare
     * @param another the string to compare this instance with.
     * @return true if master is equal to another; false otherwise.
     */
    protected boolean languageEquals(String master, String another){
        return master.equalsIgnoreCase(another);
    }
    //========================================================================

    /**
     * Converts media file from wikitext to URL. See description
     * <a href="https://commons.wikimedia.org/wiki/Commons:FAQ#What_are_the_strangely_named_components_in_file_paths.3F">here</a>
     * @param wikiUrl file name e.g. En-us-test.ogg
     * @return URL like to https://upload.wikimedia.org/wikipedia/commons/9/9c/En-us-test.ogg
     */
    public static String getMediaWikiToURL(String wikiUrl)
            throws UnsupportedEncodingException {
        String fn = normalizeFileName(wikiUrl);
        String md5 = Utils.md5(fn);
        String p1, p2;
        p1 = md5.substring(0, 1);
        p2 = md5.substring(0, 2);
        StringBuilder result = new StringBuilder();
        result.append("https://upload.wikimedia.org/wikipedia/commons/")
                .append(p1).append("/").append(p2).append("/")
                .append(fn);

        return URLDecoder.decode(result.toString(), "UTF-8");
    }
    //========================================================================
    /**
     * Normalize file name to mediaWiki standards
     * See <a href="https://commons.wikimedia.org/wiki/Commons:FAQ#What_are_the_strangely_named_components_in_file_paths.3F">this article</a>  for details.
     * @param name filename
     * @return normalized filename
     */
    public static String normalizeFileName(String name){
        String res = name.trim().replace(" ", "_");
        return Utils.toTitle(res);
    }
    //========================================================================

    /**
     * Create new {@link AudioSample} instance from given file name
     * @param audioFileName File name
     * @return New instance contains generated file URL and without comment.
     */
     AudioSample newASInstance(String audioFileName){
        audioFileName = normalizeFileName(audioFileName);
        AudioSample a = new AudioSample(Utils.stripExtension(audioFileName));
        try {
            a.setUrl(getMediaWikiToURL(audioFileName));
        } catch (UnsupportedEncodingException e){
            e.printStackTrace();
        }
        return a;
    }
    //========================================================================
    /**
     * Returns languages which is being searched for IPA and audio samples.
     */
    public static Language[] getSearchLanguages(){
        return new Language [] {
                new Language("English", "English", "en", Language.RATING_BEST),
                new Language("German", "Deutsch", "de", Language.RATING_GOOD),
                new Language("French", "Français", "fr", Language.RATING_BEST),
                new Language("Spanish", "Español", "es", Language.RATING_GOOD),
        };
    }
    //========================================================================
    /**
     * Downloads and extracts the raw wikitext of a Wiktionary article using the "parse" API.
     *
     * @param word The word to fetch.
     * @return Wikitext content of the article, or empty string if not found or error occurs.
     */
    protected String loadWordArticle(String word) {
        String url = getWikiTextUrl(word);
        try {
            HttpResponse response = getUrlWithRetry(url);
            return new JSONObject(response.getBody())
                    .getJSONObject("parse")
                    .getJSONObject("wikitext")
                    .getString("*");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for retry");
        } catch (IOException | JSONException e) {
            System.err.printf("Error loading article for word: %s%n", word);
            e.printStackTrace(System.err);
        }
        return "";
    }
    
    private HttpResponse getUrlWithRetry(String url)
            throws IOException, InterruptedException {

        final long retryTimeout = System.currentTimeMillis() + HTTP_RETRY_WAIT_TIMEOUT_MS;
        HttpResponse response = null;
        for (int retry = 0; retry <= MAX_HTTP_RETRIES; retry++) {
            while (HttpRequest.isBlocked(url)) {
                if (System.currentTimeMillis() >= retryTimeout) {
                    throw new IOException("HTTP retry wait timeout");
                }
                Thread.sleep(100);
            }
            response = HttpRequest.get(url);
            if (!response.isTooManyRequests())
                break;
            System.err.printf(
                    "HTTP 429, retry after %d seconds: %s%n",
                    response.getRetryAfter(), url);
        }
        if (!response.isOk()) {
            throw new IOException(
                    "HTTP error: " + response.getStatusCode());
        }
        return response;
    }
}
Я думаю створити єдиний інтерфейс з вибором провайдера данних, або моє API, або Wiktionary, але важливо замінити виклики Wiktionary єдиним інтерфейсом
У Wiktionary API є багато власної специфіки, як то search() getWordProposals() lookup() loadWordArticle() getLanguageSection() getIPA() getAudioSamples() getTranslation()
Вони формують той json який я вавів вище як приклад.
Тепер, Wiktionary має стати просто одним із провайдерів даних. Наша ціль визначитись з інтерфейсом загального провайдера данних.

Зараз у мене є проект WordDictCore. У ньому два пакети:
package com.worddict.worddictcore; базовий, у якому описані інтерфейси та реалізація Word Language, HttpRequest, HttpResponse
і package com.worddict.wiktionarybot; з базовим класом Wiktionary та реалізаціями для різних мов. Я бачу архітектуру так:

є чотири рівні, і вони мають таку семантику
```
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
```BackendProvider
    DictionaryProvider wiktionary = getWiktionaryProvider();
    DictionaryProvider api = getApiProvider();
```
2. DictionaryProvider відповідає на питанння які мови взагалі доступні і які словники є для конкретної мови.
```
public interface DictionaryProvider {
    Language[] getLanguages(); // en, es, fr, de
    Dictionary [] getDictionaries(String source); en -> uk, uk-min, de
    Dictionary getDictionary(String source, String dictionaryId);
}
```
3. Провайдер операцій зі словами, він прив'язаний до конкретного словника
```
public interface Dictionary {
    String[] suggest(String query);
    String[] findWords(String[] words);
    WordDefinition getWord(String word);
}
```

4. Доменна модель `WordDefinition` заради якої все робиться
```
public class WordDefinition {
    private String note;
    private Pronounce pronounce;
    private ArrayList<Translation> translations;
    private ArrayList<AudioSample> audioSamples;
}
```
Вона не знає нічого про те, звідки взялося визначення.
Тоді зміни виглядають так:
```
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
