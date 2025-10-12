# Wisehome.hu Szerver Integráció

## Áttekintés

A mobilapp most a **wisehome.hu:5000** szervert használja az okosotthon parancsok elemzéséhez az OpenAI Agent Builder workflow segítségével.

## Architektúra

```
[Android App]
    ↓ HTTP POST
[wisehome.hu:5000/analyze]
    ↓ OpenAI Agents SDK
[OpenAI Agent Builder Workflow]
    ↓ JSON válasz
[Android App] → TTS felolvasás
```

## Működési folyamat

### 1. Hangrögzítés
- Felhasználó beszél: "Kapcsold be a nappaliban a lámpát"
- Wake word vagy manuális rögzítés

### 2. Átirás (Whisper API)
- Audio → OpenAI Whisper API
- Eredmény: "kapcsold be a nappaliban a lámpát"

### 3. Szerver elemzés
- **POST** → `http://wisehome.hu:5000/analyze`
- Request body:
```json
{
  "text": "kapcsold be a nappaliban a lámpát"
}
```

### 4. Szerver válasz
- Response:
```json
{
  "success": true,
  "result": {
    "helyiség": "nappali",
    "eszköz": "lámpa",
    "parancs": "bekapcsol"
  }
}
```

### 5. JSON feldolgozás
- MainActivity parse-olja a JSON-t
- Ember-barát válasz generálás
- Példa: "Rendben, bekapcsolom a lámpa eszközt a nappaliban."

### 6. TTS felolvasás
- Android TTS (gyors) vagy OpenAI TTS (minőség)
- A felhasználó hallja a választ

## OpenAiWorkflowService.kt változások

### Régi implementáció:
```kotlin
private val apiUrl = "https://api.openai.com/v1/chat/completions"
// Közvetlenül hívta az OpenAI API-t
```

### Új implementáció:
```kotlin
private val serverUrl = "http://wisehome.hu:5000/analyze"

val jsonBody = JSONObject().apply {
    put("text", userMessage)
}

val request = Request.Builder()
    .url(serverUrl)
    .post(requestBody)
    .build()
```

## Előnyök

### ✅ Központosított AI logika
- Minden AI intelligencia a szerveren
- Könnyű frissítés (csak a szervert kell módosítani)
- Ugyanaz a logika minden kliens számára

### ✅ API kulcs biztonság
- OpenAI API kulcs **csak a szerveren**
- Nem kell az Android app-ban tárolni
- Biztonságosabb production környezet

### ✅ Költség kontroll
- Rate limiting a szerveren
- Költség monitoring központosítva
- Felhasználónkénti kvóták

### ✅ Skálázhatóság
- Agent logika frissítése server-side
- Új funkciók server deploymenttel
- Nincs app frissítés szükséges

### ✅ Offline fallback (jövőbeli)
- Ha a szerver nem elérhető, helyi fallback
- Cache-elt válaszok

## Konfiguráció

### Android app
**File:** `OpenAiWorkflowService.kt`
```kotlin
private val serverUrl = "http://wisehome.hu:5000/analyze"
```

### Szerver
**Location:** wisehome.hu
**Port:** 5000
**Endpoint:** /analyze
**GitHub:** https://github.com/bogoomil/wisehome-openai-workflow

## API Kommunikáció

### Request Format
```kotlin
POST http://wisehome.hu:5000/analyze
Content-Type: application/json

{
  "text": "kapcsold be a lámpát"
}
```

### Response Format (Success)
```json
{
  "success": true,
  "result": {
    "helyiség": "nappali",
    "eszköz": "lámpa",
    "parancs": "bekapcsol"
  }
}
```

### Response Format (Error)
```json
{
  "success": false,
  "error": "Error message here"
}
```

## Hálózati konfiguráció

### Android Manifest
Győződj meg róla, hogy az `AndroidManifest.xml` engedélyezi az internet hozzáférést:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Cleartext traffic (HTTP)
Android 9+ (API 28+) alapértelmezetten blokkolja a HTTP forgalmat. 

**Megoldás 1:** `AndroidManifest.xml`-ben:
```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

**Megoldás 2:** Network Security Config (ajánlott):
`res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">wisehome.hu</domain>
    </domain-config>
</network-security-config>
```

`AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

## Teljesítmény

**Becsült válaszidők:**
1. Audio rögzítés: 3-5 sec
2. Whisper átirás: 1-3 sec  
3. **Szerver elemzés: 2-4 sec** ⬅ Új
4. TTS felolvasás: 1-2 sec vagy instant

**Összesen:** 7-14 sec

**Hálózat függőség:**
- Gyors WiFi: ~2 sec
- Lassú mobil: ~5 sec
- Rossz kapcsolat: timeout (120 sec)

## Timeout beállítások

Az OkHttp client timeout-jai:

```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
```

## Hibakezelés

### Szerver nem elérhető
```kotlin
catch (ConnectException e) {
    Log.e("OpenAiWorkflow", "Cannot connect to wisehome.hu")
    return "A szerver nem elérhető. Ellenőrizd az internet kapcsolatot."
}
```

### Timeout
```kotlin
catch (SocketTimeoutException e) {
    Log.e("OpenAiWorkflow", "Server timeout")
    return "A szerver válasza túl sokáig tart."
}
```

### JSON parsing hiba
```kotlin
catch (JSONException e) {
    Log.e("OpenAiWorkflow", "Invalid JSON response")
    return "Érvénytelen válasz a szervertől."
}
```

## Debug

### Android Studio Logcat
```
Szűrő: "OpenAiWorkflow"
```

**Keresendő üzenetek:**
- `Sending to wisehome.hu server` - Küldés indult
- `Server URL: http://wisehome.hu:5000/analyze` - Endpoint
- `Response received: {...}` - Szerver válasz
- `Parsed result: {...}` - Feldolgozott JSON

### Példa log kimenet:
```
D/OpenAiWorkflow: Sending to wisehome.hu server
D/OpenAiWorkflow: User message: kapcsold be a lámpát
D/OpenAiWorkflow: Server URL: http://wisehome.hu:5000/analyze
D/OpenAiWorkflow: Sending request to wisehome.hu...
D/OpenAiWorkflow: Response received: {"success":true,"result":{...}}
D/OpenAiWorkflow: Parsed result: {"helyiség":"nappali","eszköz":"lámpa","parancs":"bekapcsol"}
```

## Szerver URL módosítása

Ha másik szervert szeretnél használni, módosítsd:

**Lokális tesztelés:**
```kotlin
private val serverUrl = "http://10.0.2.2:5000/analyze"  // Android emulátor
// vagy
private val serverUrl = "http://192.168.1.100:5000/analyze"  // Saját géped IP
```

**Production:**
```kotlin
private val serverUrl = "https://wisehome.hu/api/analyze"  // HTTPS ajánlott!
```

**BuildConfig használat (ajánlott):**
```gradle
// build.gradle
buildConfigField "String", "SERVER_URL", "\"${localProperties.getProperty('SERVER_URL', 'http://wisehome.hu:5000/analyze')}\""
```

```kotlin
// OpenAiWorkflowService.kt
private val serverUrl = BuildConfig.SERVER_URL
```

## Költségek

### Régi (közvetlen OpenAI hívás az app-ból):
- Whisper: $0.006 / perc
- GPT-4o-mini: $0.15 / 1M input, $0.60 / 1M output
- **Probléma:** API kulcs az app-ban

### Új (wisehome.hu szerver):
- Whisper: $0.006 / perc (még mindig az app-ból)
- Szerver elemzés: $0.002 / kérés
- **Előny:** API kulcs biztonságban a szerveren

## Biztonság

### ✅ Előnyök:
- OpenAI API kulcs **NEM az app-ban**
- Szerver-side rate limiting
- Központi logging és monitoring
- IP whitelist lehetőség a szerveren

### ⚠️ Fontos:
- HTTP helyett használj **HTTPS**-t production-ben!
- Implementálj autentikációt (API key, JWT token)
- Naplózd minden kérést server-oldalon

## Production ajánlások

### 1. HTTPS használata
```kotlin
private val serverUrl = "https://wisehome.hu/api/analyze"
```

Szerveren nginx reverse proxy SSL certificate-tel.

### 2. Authentication
**API Key fejlécben:**
```kotlin
val request = Request.Builder()
    .url(serverUrl)
    .addHeader("X-API-Key", "your-app-api-key")
    .post(requestBody)
    .build()
```

**Szerver ellenőrzés:**
```python
@app.route('/analyze', methods=['POST'])
def analyze_endpoint():
    api_key = request.headers.get('X-API-Key')
    if api_key != os.environ.get('VALID_API_KEY'):
        return jsonify({"success": False, "error": "Unauthorized"}), 401
    # ...
```

### 3. Rate Limiting
```python
from flask_limiter import Limiter

limiter = Limiter(app, default_limits=["100 per hour"])

@app.route('/analyze', methods=['POST'])
@limiter.limit("20 per minute")
def analyze_endpoint():
    # ...
```

## Tesztelés

### Lokális szerver tesztelés
```bash
# Szerver indítása
cd /home/kunb/CursorProjects/openai-agent
docker-compose up -d

# Teszt curl-lel
curl -X POST http://localhost:5000/analyze \
  -H "Content-Type: application/json" \
  -d '{"text": "kapcsold be a lámpát"}'
```

### Android app tesztelés
1. Módosítsd átmenetileg a `serverUrl`-t:
```kotlin
private val serverUrl = "http://10.0.2.2:5000/analyze"  // Lokális szerver
```

2. Build és install az app
3. Próbáld ki a hangrögzítést
4. Nézd a logokat Logcat-ben

## Changelog

**2024.10.12**
- ✅ OpenAiWorkflowService.kt átírva wisehome.hu szerver használatra
- ✅ Eltávolítva az OpenAI API közvetlen hívása
- ✅ Egyszerűsített request/response formátum
- ✅ Részletes hibakezelés és logging
- ✅ Dokumentáció (WISEHOME_SERVER_INTEGRATION.md)

## Következő lépések

1. ✅ Mobilapp build és telepítés
2. ⚠️ Teszteld lokálisan (10.0.2.2:5000)
3. ✅ Teszteld a wisehome.hu szerverrel
4. 🔜 HTTPS implementálása (ajánlott!)
5. 🔜 Autentikáció hozzáadása
6. 🔜 Rate limiting

## Támogatás

**Problémák esetén:**
1. Ellenőrizd a szerver fut-e: `curl http://wisehome.hu:5000/health`
2. Nézd meg az Android logokat: Logcat → "OpenAiWorkflow"
3. Teszteld curl-lel külön
4. Ellenőrizd az internet kapcsolatot az app-ban

