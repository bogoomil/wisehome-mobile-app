# JSON Parancs Rendszer

## Áttekintés

Az AI most **strukturált JSON formátumban** válaszol, ami lehetővé teszi az eszközvezérlési parancsok precíz feldolgozását.

## JSON Formátum

```json
{
  "helyiseg": "nappali",
  "eszkoz": "lámpa",
  "parancs": "bekapcsol"
}
```

### Mezők

| Mező | Leírás | Példa értékek |
|------|--------|---------------|
| **helyiseg** | A helyiség neve, ahol az eszköz található | `"nappali"`, `"hálószoba"`, `"konyha"`, `"fürdőszoba"`, `"összes"` |
| **eszkoz** | Az eszköz típusa | `"lámpa"`, `"fény"`, `"fűtés"`, `"redőny"`, `"klíma"`, `"ajtó"` |
| **parancs** | A végrehajtandó művelet | `"bekapcsol"`, `"kikapcsol"`, `"22 fok"`, `"kinyit"`, `"bezár"` |

## Példa beszélgetések

### 1. Lámpa vezérlés
**Input:** "Kapcsold be a nappaliban a lámpát"
**JSON válasz:**
```json
{
  "helyiseg": "nappali",
  "eszkoz": "lámpa",
  "parancs": "bekapcsol"
}
```
**TTS felolvasás:** "Rendben, bekapcsolom a lámpa eszközt a nappaliban."

---

### 2. Fűtés beállítás
**Input:** "Állítsd 22 fokra a fűtést a hálószobában"
**JSON válasz:**
```json
{
  "helyiseg": "hálószoba",
  "eszkoz": "fűtés",
  "parancs": "22 fok"
}
```
**TTS felolvasás:** "Rendben, beállítom a fűtés eszközt 22 fok értékre a hálószobában."

---

### 3. Redőny vezérlés
**Input:** "Nyisd ki a nappali redőnyöket"
**JSON válasz:**
```json
{
  "helyiseg": "nappali",
  "eszkoz": "redőny",
  "parancs": "kinyit"
}
```
**TTS felolvasás:** "Rendben, kinyitom a redőny eszközt a nappaliban."

---

### 4. Minden lámpa kikapcsolása
**Input:** "Kapcsold ki az összes lámpát"
**JSON válasz:**
```json
{
  "helyiseg": "összes",
  "eszkoz": "lámpa",
  "parancs": "kikapcsol"
}
```
**TTS felolvasás:** "Rendben, kikapcsolom a lámpa eszközt az összesban."

---

### 5. Klíma beállítás
**Input:** "Kapcsold be a klímát a konyhában"
**JSON válasz:**
```json
{
  "helyiseg": "konyha",
  "eszkoz": "klíma",
  "parancs": "bekapcsol"
}
```
**TTS felolvasás:** "Rendben, bekapcsolom a klíma eszközt a konyhában."

## Implementáció részletei

### 1. OpenAI System Prompt

A GPT-4o-mini modell a következő system prompt-ot kapja:

```
Te egy okosotthon eszköz vezérlő asszisztens vagy.
A felhasználó magyar nyelvű hangparancsokat ad.

A feladatod: Elemezd a parancsot és válaszolj JSON formátumban:
{"helyiseg": "", "eszkoz": "", "parancs": ""}

Példák:
- "Kapcsold be a nappaliban a lámpát" → {"helyiseg": "nappali", "eszkoz": "lámpa", "parancs": "bekapcsol"}
- "Kapcsold ki a hálószobában a fényt" → {"helyiseg": "hálószoba", "eszkoz": "fény", "parancs": "kikapcsol"}
- "Állítsd 22 fokra a fűtést a konyhában" → {"helyiseg": "konyha", "eszkoz": "fűtés", "parancs": "22 fok"}

Ha nincs helyiség megadva, használd: "összes" vagy "általános"
Ha nem egyértelmű a parancs, használd: "ismeretlen"

FONTOS: CSAK JSON-t adj vissza, semmi mást!
```

### 2. OpenAI API beállítások

```kotlin
put("model", "gpt-4o-mini")
put("temperature", 0.3)  // Alacsony = konzisztens JSON
put("max_tokens", 100)
put("response_format", JSONObject().apply {
    put("type", "json_object")  // Garantált JSON válasz
})
```

### 3. MainActivity JSON Parsing

```kotlin
private fun parseJsonResponse(jsonResponse: String): Pair<String, Map<String, String>> {
    val json = JSONObject(jsonResponse)
    val helyiseg = json.optString("helyiseg", "ismeretlen")
    val eszkoz = json.optString("eszkoz", "ismeretlen")
    val parancs = json.optString("parancs", "ismeretlen")
    
    // Ember-barát válasz generálás
    val humanResponse = when {
        parancs == "bekapcsol" -> "Rendben, bekapcsolom a $eszkoz eszközt a ${helyiseg}ban."
        parancs == "kikapcsol" -> "Rendben, kikapcsolom a $eszkoz eszközt a ${helyiseg}ban."
        parancs.contains("fok") -> "Rendben, beállítom a $eszkoz eszközt $parancs értékre a ${helyiseg}ban."
        // ... további esetek
    }
    
    return Pair(humanResponse, mapOf(...))
}
```

### 4. UI Frissítés

A státusz szöveg mutatja a parsed adatokat:
```
"Parancs: nappali - lámpa - bekapcsol"
```

A TTS felolvassa az ember-barát választ.

## Valódi eszközvezérlés implementálása

Most már könnyen hozzáadhatsz valódi eszközvezérlést!

### Példa: Home Assistant integráció

```kotlin
private suspend fun executeDeviceControl(
    helyiseg: String,
    eszkoz: String,
    parancs: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val homeAssistantUrl = "http://your-home-assistant:8123/api/services"
            val token = "your_long_lived_access_token"
            
            // Eszköz ID összeállítása
            val entityId = when (eszkoz) {
                "lámpa", "fény" -> "light.${helyiseg}_lamp"
                "fűtés" -> "climate.${helyiseg}_thermostat"
                "redőny" -> "cover.${helyiseg}_blinds"
                else -> return@withContext false
            }
            
            // Szolgáltatás meghatározása
            val (domain, service) = when (parancs) {
                "bekapcsol" -> Pair("homeassistant", "turn_on")
                "kikapcsol" -> Pair("homeassistant", "turn_off")
                "kinyit" -> Pair("cover", "open_cover")
                "bezár" -> Pair("cover", "close_cover")
                else -> Pair("homeassistant", "turn_on")
            }
            
            val jsonBody = JSONObject().apply {
                put("entity_id", entityId)
                if (parancs.contains("fok")) {
                    val temperature = parancs.replace(" fok", "").toIntOrNull()
                    put("temperature", temperature)
                }
            }
            
            val request = Request.Builder()
                .url("$homeAssistantUrl/$domain/$service")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("DeviceControl", "Error controlling device", e)
            false
        }
    }
}
```

### Integráció a MainActivity-be

```kotlin
// Parse JSON response
val (humanResponse, jsonData) = parseJsonResponse(workflowResponse)

// Execute real device control
val success = executeDeviceControl(
    jsonData["helyiseg"] ?: "",
    jsonData["eszkoz"] ?: "",
    jsonData["parancs"] ?: ""
)

// Update human response based on success
val finalResponse = if (success) {
    humanResponse
} else {
    "Nem sikerült végrehajtani a parancsot."
}

// Speak the response
androidTtsService.speak(finalResponse)
```

## Előnyök

### ✅ Strukturált adatok
- Könnyű feldolgozás
- Típusbiztos parsing
- Előre definiált mezők

### ✅ Kiterjeszthető
- Új eszköztípusok hozzáadása egyszerű
- Új parancsok könnyen implementálhatók
- Több paraméter támogatása (pl. fényerő, szín)

### ✅ Debug-olható
- JSON logban látható
- UI-ban megjelenik a parsed adat
- Könnyen tesztelhető

### ✅ Intelligens feldolgozás
- GPT-4o-mini értelmezi a természetes nyelvet
- Szinonimák kezelése (lámpa ≈ fény)
- Helyesírási hibák tolerálása

## Továbbfejlesztési lehetőségek

### 1. Több paraméter támogatása
```json
{
  "helyiseg": "nappali",
  "eszkoz": "lámpa",
  "parancs": "bekapcsol",
  "fenyero": "50%",
  "szin": "meleg fehér"
}
```

### 2. Több eszköz egy parancsban
```json
{
  "parancsok": [
    {"helyiseg": "nappali", "eszkoz": "lámpa", "parancs": "kikapcsol"},
    {"helyiseg": "hálószoba", "eszkoz": "lámpa", "parancs": "kikapcsol"}
  ]
}
```

### 3. Időzítés támogatása
```json
{
  "helyiseg": "nappali",
  "eszkoz": "lámpa",
  "parancs": "bekapcsol",
  "ido": "19:00"
}
```

### 4. Feltételes logika
```json
{
  "feltetel": "ha otthon vagyok",
  "parancs": {"helyiseg": "nappali", "eszkoz": "lámpa", "parancs": "bekapcsol"}
}
```

### 5. Visszajelzés az eszköz állapotáról
```json
{
  "helyiseg": "nappali",
  "eszkoz": "lámpa",
  "parancs": "statusz",
  "valasz": {"allapot": "bekapcsolva", "fenyero": "80%"}
}
```

## Tesztelés

### Logok figyelése

```bash
adb logcat -s MainActivity:D OpenAiWorkflow:D
```

**Keresendő sorok:**
- `AI JSON response: {...}` - Nyers JSON az AI-tól
- `Parsed - Helyiség: X, Eszköz: Y, Parancs: Z` - Parsed adatok
- `Human response: ...` - Emberbarát válasz

### Példa teszt szkript

**Tesztelendő parancsok:**
1. "Kapcsold be a nappaliban a lámpát"
2. "Kapcsold ki az összes fényt"
3. "Állítsd 23 fokra a fűtést"
4. "Nyisd ki a hálószobában a redőnyöket"
5. "Kapcsold be a klímát a konyhában"

**Elvárt eredmények:**
- ✅ JSON válasz minden parancsra
- ✅ Helyes helyiség, eszköz, parancs értékek
- ✅ Természetes nyelvi TTS válasz
- ✅ UI frissítés a parsed adatokkal

## Költségek

**JSON mode használat:**
- Nincs extra költség
- Ugyanaz, mint a normál Chat Completions
- ~$0.001 / parancs

## Biztonság

### API kulcsok
- ✅ `local.properties`-ben tárolva
- ✅ BuildConfig használata
- ✅ Nem kerül verziókezelésbe

### JSON validáció
- ✅ Try-catch minden JSON parsing műveletnél
- ✅ Default értékek, ha a parsing sikertelen
- ✅ Részletes hibalogolás

### Eszközvezérlés
- ⚠️ Ellenőrizd a parancsokat végrehajtás előtt
- ⚠️ Használj whitelist-et az engedélyezett eszközökhöz
- ⚠️ Implementálj rate limiting-et
- ⚠️ Logold minden eszközvezérlési kísérletet

## Összefoglaló

Az új JSON alapú rendszer:
- ✅ **Strukturált** - Könnyű feldolgozás
- ✅ **Intelligens** - GPT-4o-mini értelmezi a természetes nyelvet
- ✅ **Kiterjeszthető** - Új eszközök és parancsok egyszerűen hozzáadhatók
- ✅ **Megbízható** - JSON mode garantálja a formátumot
- ✅ **Felhasználóbarát** - Természetes nyelvi TTS válasz

Most már készen állsz valódi okosotthon vezérlés implementálására! 🏠✨

