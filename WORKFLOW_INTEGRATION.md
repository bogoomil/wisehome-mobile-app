# OpenAI AI Integráció

## Áttekintés

A mobilapp **OpenAI Chat Completions API** integrációval rendelkezik (GPT-4o-mini modell). Ez egy gyors, költséghatékony és stabil megoldás intelligens válaszok generálására.

> **Megjegyzés:** Eredetileg workflow API-t terveztünk, de mivel az még béta/nem publikus, a stabil Chat Completions API-t használjuk helyette. Ez ugyanazt a funkcionalitást biztosítja.

## Chat Completions API

A mobilapp a **Chat Completions API**-t használja:

| Tulajdonság | Érték |
|------------|-------|
| **Modell** | gpt-4o-mini |
| **Válaszidő** | 2-5 másodperc |
| **Költség** | ~$0.002 / kérés |
| **Stabilitás** | ✅ Production-ready |
| **System prompt** | Magyar okosotthon asszisztens |

## A mobilapp implementáció

### Jelenlegi megvalósítás

A mobilapp a `OpenAiWorkflowService.kt` osztályt használja (a név maradt, de már Chat API-t használ):

**Működési folyamat:**
1. Hang rögzítése
2. Whisper API → Átirás
3. **Átirás elküldése a GPT-4o-mini modellnek**
4. AI feldolgozás (system prompt alapján)
5. **AI válaszának felolvasása TTS-sel**

**System Prompt:**
```
Te egy segítőkész magyar nyelvű okosotthon asszisztens vagy.
A felhasználó hangparancsokat ad neked.
Válaszolj röviden, tömören, mert a válasz hangban lesz felolvasva.
Legfeljebb 2-3 mondatban válaszolj.
Udvarias és barátságos legyél.
```

## OpenAiWorkflowService.kt részletei

### Főbb funkciók

```kotlin
suspend fun sendMessageToWorkflow(userMessage: String): String
```

Ez a függvény:
1. Küld egy chat completion kérést a GPT-4o-mini modellnek
2. System prompt + felhasználói üzenet
3. Visszaadja az AI választ

### API Endpoint

```
POST https://api.openai.com/v1/chat/completions
```

**Request Body:**
```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "system",
      "content": "Te egy segítőkész magyar nyelvű okosotthon asszisztens vagy..."
    },
    {
      "role": "user",
      "content": "kapcsold be a lámpát"
    }
  ],
  "temperature": 0.7,
  "max_tokens": 150
}
```

### Headers
```
Authorization: Bearer {OPENAI_API_KEY}
Content-Type: application/json
```

### Hibakezelés

- **Hibák kezelése:**
  - HTTP hibakódok naplózása
  - Magyar nyelvű hibaüzenetek
  - Részletes logolás (OpenAiWorkflow tag)
  - Exception handling minden szinten

## System Prompt testreszabása

A system prompt a kódban hardcoded, de könnyen módosítható:

**Jelenlegi:**
```kotlin
put("content", """
    Te egy segítőkész magyar nyelvű okosotthon asszisztens vagy.
    A felhasználó hangparancsokat ad neked.
    Válaszolj röviden, tömören, mert a válasz hangban lesz felolvasva.
    Legfeljebb 2-3 mondatban válaszolj.
    Udvarias és barátságos legyél.
""".trimIndent())
```

**Testreszabási példák:**

1. **Okosotthon szakértő:**
```kotlin
"""
Te egy okosotthon szakértő vagy, aki ismeri a Philips Hue, IKEA TRÅDFRI, 
és más okosotthon rendszereket. Segíts a felhasználónak eszközök vezérlésében.
Válaszolj magyarul, röviden.
"""
```

2. **Vicces asszisztens:**
```kotlin
"""
Te egy vicces, szórakoztató okosotthon asszisztens vagy.
Viccesen, de hasznosankedd segíts. Használj emotikonokat néha.
Válaszolj magyarul, 2-3 mondatban.
"""
```

3. **Formális asszisztens:**
```kotlin
"""
Ön egy professzionális okosotthon rendszer vezérlő asszisztens.
Tegezzen a felhasználót és pontos, technikai információkat adjon.
Válaszoljon magyarul, tömören.
"""
```

## Kód integráció

### MainActivity módosítások

```kotlin
// Szolgáltatás inicializálása
workflowService = OpenAiWorkflowService()

// Használat
val workflowResponse = withContext(Dispatchers.IO) {
    workflowService.sendMessageToWorkflow(transcription)
}

// TTS lejátszás
androidTtsService.speak(workflowResponse) {
    // Befejezés után
}
```

## Válasz formátum kezelés

A `OpenAiWorkflowService` több lehetséges válasz struktúrát kezel:

```json
// Lehetőség 1
{
  "output": {
    "message": "A lámpa bekapcsolva"
  }
}

// Lehetőség 2
{
  "output": {
    "text": "A lámpa bekapcsolva"
  }
}

// Lehetőség 3
{
  "result": "A lámpa bekapcsolva"
}

// Lehetőség 4
{
  "response": "A lámpa bekapcsolva"
}
```

A service automatikusan megtalálja és kinyeri a választ.

## Teljesítmény

**Becsült válaszidők:**
- Whisper átirás: 1-3 sec
- GPT-4o-mini válasz: 2-5 sec
- TTS generálás: 1-2 sec (OpenAI) vagy instant (Android)

**Összesen:** 4-10 másodperc

**Optimalizálási tippek:**
- GPT-4o-mini már optimalizált gyorsaságra
- Max tokens limit csökkentése (jelenleg 150)
- Android TTS használata OpenAI TTS helyett (instant)

## Költségek

**OpenAI API díjak (2024):**
- Whisper: ~$0.006 / perc
- GPT-4o-mini: $0.15 / 1M input tokens, $0.60 / 1M output tokens
- TTS: ~$0.015 / 1K karakter (ha OpenAI TTS-t használsz)

**Példa költség egy interakció:**
- 5 sec hang: $0.0005
- GPT-4o-mini (50 token input + 100 token output): $0.000068
- TTS (Android - ingyenes vagy OpenAI ~$0.00075): $0 vagy $0.00075
- **Összesen:** ~$0.001 / interakció (nagyon olcsó! 🎉)

**Havi becsült költség:**
- 100 interakció/hó: ~$0.10
- 1000 interakció/hó: ~$1.00
- 10,000 interakció/hó: ~$10.00

## Debug és hibaelhárítás

### Logok ellenőrzése

```bash
adb logcat | grep "OpenAiWorkflow"
```

**Fontos log üzenetek:**
- "Starting workflow execution" - Kezdés
- "Workflow run started: {run_id}" - Futtatás indult
- "Run status: {status}" - Állapot frissítés
- "Workflow result: {result}" - Végeredmény

### Gyakori hibák

#### 1. "Failed to execute workflow"
**Okok:**
- Érvénytelen workflow ID
- API kulcs probléma
- Nincs internet

**Megoldás:**
```bash
# Ellenőrizd a workflow ID-t
cat local.properties | grep WORKFLOW

# Teszteld az API kulcsot
curl -H "Authorization: Bearer $OPENAI_API_KEY" \
     https://api.openai.com/v1/models
```

#### 2. "Workflow did not complete within timeout"
**Okok:**
- Túl komplex workflow
- Lassú internet
- OpenAI API túlterhelt

**Megoldás:**
- Növeld a timeout-ot (jelenleg 60 sec)
- Egyszerűsítsd a workflow-t
- Próbáld később

#### 3. "Could not find result in expected fields"
**Ok:** A workflow nem a várt formátumban adja vissza az eredményt

**Megoldás:**
1. Nézd meg a teljes választ a logban
2. Módosítsd az `extractWorkflowResult()` függvényt
3. Vagy alakítsd át a workflow output-ját

### Példa debug session

```bash
# Teljes debug log
adb logcat -v time | grep -E "(OpenAiWorkflow|MainActivity)"

# Csak hibák
adb logcat *:E | grep OpenAiWorkflow

# Workflow specifikus
adb logcat -s OpenAiWorkflow:D
```

## Továbbfejlesztési lehetőségek

### 1. Cache-elés
```kotlin
private val responseCache = mutableMapOf<String, String>()

fun sendMessageToWorkflow(message: String): String {
    // Először nézd meg a cache-ben
    responseCache[message]?.let { return it }
    
    // Különben hívd meg a workflow-t
    val result = actualWorkflowCall(message)
    responseCache[message] = result
    return result
}
```

### 2. Streaming válaszok
Ha az OpenAI támogatja a workflow streaming-et:
```kotlin
suspend fun streamWorkflowResponse(
    message: String,
    onToken: (String) -> Unit
)
```

### 3. Batch feldolgozás
Több kérés egyszerre:
```kotlin
suspend fun sendBatchToWorkflow(
    messages: List<String>
): List<String>
```

### 4. Offline fallback
```kotlin
fun sendMessageToWorkflow(message: String): String {
    return if (isOnline()) {
        workflowCall(message)
    } else {
        offlineFallback(message)
    }
}
```

### 5. Context megőrzés
Session ID-val több interakció összekapcsolása:
```kotlin
class WorkflowSession(val sessionId: String) {
    suspend fun sendMessage(message: String): String {
        // Session kontextussal hívás
    }
}
```

## Biztonság

### Best practices

✅ **DO:**
- Tárold a workflow ID-t `local.properties`-ben
- Használj BuildConfig-ot fordításkor
- Ne commitold az API kulcsokat
- Production-ben backend API-t használj

❌ **DON'T:**
- Ne hardcodeold a workflow ID-t
- Ne tárolj API kulcsokat az APK-ban
- Ne oszd meg nyilvánosan a workflow ID-t

### Production architektúra

```
[Android App] 
    ↓ HTTPS
[Backend API] (saját szerver)
    ↓ API kulccsal
[OpenAI Workflow API]
```

**Előnyök:**
- API kulcs biztonságban a szerveren
- Rate limiting
- Költség kontroll
- Felhasználó autentikáció

## Workflow példák

### 1. Egyszerű válasz workflow
```
Input: message
  ↓
LLM (GPT-4): "Válaszolj röviden magyarul: {message}"
  ↓
Output: response
```

### 2. Okosotthon vezérlő workflow
```
Input: message
  ↓
Intent Classifier: Mire gondolt a felhasználó?
  ↓ (branch)
  ├─ Eszköz vezérlés → API hívás → Visszajelzés
  ├─ Kérdés → LLM válasz
  └─ Egyéb → Általános válasz
  ↓
Output: message
```

### 3. Multi-step workflow
```
Input: message
  ↓
1. Nyelv felismerés
  ↓
2. Fordítás angolra
  ↓
3. Intent analysis
  ↓
4. Action execution
  ↓
5. Fordítás magyarra
  ↓
Output: response
```

## API referencia

### Hivatalos dokumentáció

- [OpenAI Workflows (ha elérhető)](https://platform.openai.com/docs/workflows)
- [Agent Builder](https://platform.openai.com/agents)
- [API Reference](https://platform.openai.com/docs/api-reference)

### Támogatás

Ha problémád van:
1. Nézd meg a debug logokat
2. Ellenőrizd a workflow konfigurációt az Agent Builder-ben
3. Teszteld a workflow-t a webes felületen
4. Nyiss issue-t vagy kérdezz a csapatban

## Changelog

**2024.10.12**
- ✅ OpenAiWorkflowService.kt létrehozva
- ✅ MainActivity átalakítva workflow használatra
- ✅ build.gradle OPENAI_WORKFLOW_ID támogatás
- ✅ local.properties frissítve a workflow ID-val
- ✅ Alternatív endpoint támogatás
- ✅ Részletes hibakezelés és logging
- ✅ Dokumentáció (WORKFLOW_INTEGRATION.md)

