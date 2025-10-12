# Mobilapp + Wisehome.hu Szerver - Telepítési Összefoglaló

## 🎯 Rendszer áttekintés

```
┌─────────────────┐
│  Android App    │
│  (mobilapp)     │
└────────┬────────┘
         │ 1. Hangrögzítés + Whisper átirás
         ↓
    "kapcsold be a lámpát"
         │
         │ 2. HTTP POST
         ↓
┌─────────────────────┐
│  wisehome.hu:5000   │
│  (OpenAI Agent API) │
└────────┬────────────┘
         │ 3. OpenAI Agent Builder
         ↓
┌─────────────────────┐
│   GPT-4o-mini       │
│   (Agent workflow)  │
└────────┬────────────┘
         │ 4. JSON válasz
         ↓
    {"helyiség": "nappali", "eszköz": "lámpa", "parancs": "bekapcsol"}
         │
         │ 5. Parse + ember-barát válasz
         ↓
    "Rendben, bekapcsolom a lámpa eszközt a nappaliban."
         │
         │ 6. TTS felolvasás
         ↓
    🔊 Hangos visszajelzés
```

## 📱 1. Mobilapp beállítása

### A. Telepítés
```bash
cd /home/kunb/CursorProjects/mobilapp

# Build
./gradlew assembleDebug

# APK helye
app/build/outputs/apk/debug/app-debug.apk
```

### B. Konfiguráció
**Fájl:** `local.properties`
```properties
OPENAI_API_KEY=sk-proj-...  # Whisper átiráshoz
OPENAI_WORKFLOW_ID=wf_...   # Már NEM használt
PICOVOICE_ACCESS_KEY=Ny7... # Wake word-höz
```

### C. Kulcs osztályok
- **MainActivity.kt** - Fő UI és koordináció
- **OpenAiWorkflowService.kt** - ⭐ wisehome.hu szerver hívás
- **TranscriptionService.kt** - Whisper átirás
- **OpenAiTtsService.kt** / **AndroidTtsService.kt** - TTS
- **WakeWordService.kt** - Wake word detection

### D. Szerver URL
```kotlin
// OpenAiWorkflowService.kt
private val serverUrl = "http://wisehome.hu:5000/analyze"
```

## 🖥️ 2. Szerver beállítása (wisehome.hu)

### A. Repository klónozása
```bash
git clone https://github.com/bogoomil/wisehome-openai-workflow.git
cd wisehome-openai-workflow
```

### B. Környezeti változók
```bash
cp .env.example .env
nano .env
```

Töltsd ki:
```
OPENAI_API_KEY=sk-proj-your-actual-key-here
PORT=5000
DEBUG=False
```

### C. Docker indítás
```bash
docker-compose up -d --build
```

### D. Ellenőrzés
```bash
# Health check
curl http://localhost:5000/health

# API teszt
curl -X POST http://localhost:5000/analyze \
  -H "Content-Type: application/json" \
  -d '{"text": "kapcsold be a lámpát"}'
```

## 🔌 3. Teljes folyamat tesztelése

### Szerver oldalon:
```bash
# Logok figyelése
docker-compose logs -f
```

### Mobilapp oldalon:
1. Indítsd el az app-ot
2. Mondd: **"Hello Al"** (wake word)
3. Beszélj: **"Kapcsold be a nappaliban a lámpát"**
4. Figyeld a logokat:

**Android Studio Logcat:**
```
D/OpenAiWorkflow: Sending to wisehome.hu server
D/OpenAiWorkflow: User message: kapcsold be a nappaliban a lámpát
D/OpenAiWorkflow: Response received: {"success":true,"result":{...}}
D/MainActivity: Parsed - Helyiség: nappali, Eszköz: lámpa, Parancs: bekapcsol
```

**Szerver logs:**
```
INFO:app:Analyzing command: kapcsold be a nappaliban a lámpát
INFO:app:Analysis result: {'helyiség': 'nappali', 'eszköz': 'lámpa', 'parancs': 'bekapcsol'}
```

## 📊 4. API Kommunikáció

### Request (Android → Server)
```http
POST http://wisehome.hu:5000/analyze
Content-Type: application/json

{
  "text": "kapcsold be a nappaliban a lámpát"
}
```

### Response (Server → Android)
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

### Android feldolgozás
```kotlin
val helyiseg = result.optString("helyiség", "")
val eszkoz = result.optString("eszköz", "")
val parancs = result.optString("parancs", "")

// Ember-barát válasz
val humanResponse = "Rendben, bekapcsolom a $eszkoz eszközt a ${helyiseg}ban."

// TTS felolvasás
androidTtsService.speak(humanResponse)
```

## 🎤 5. Használati példák

### Példa 1: Lámpa vezérlés
**Bemenet:** "Kapcsold be a nappaliban a lámpát"

**Szerver JSON:**
```json
{"helyiség": "nappali", "eszköz": "lámpa", "parancs": "bekapcsol"}
```

**Felolvasás:** "Rendben, bekapcsolom a lámpa eszközt a nappaliban."

---

### Példa 2: Fűtés beállítás
**Bemenet:** "Állítsd 22 fokra a fűtést"

**Szerver JSON:**
```json
{"helyiség": "nappali", "eszköz": "fűtés", "parancs": "állítsd 22 fokra"}
```

**Felolvasás:** "Rendben, beállítom a fűtés eszközt állítsd 22 fokra értékre a nappaliban."

---

### Példa 3: Minden lámpa
**Bemenet:** "Kapcsold ki az összes lámpát"

**Szerver JSON:**
```json
{"helyiség": "bármely", "eszköz": "lámpák", "parancs": "ki"}
```

**Felolvasás:** "Rendben, ki parancsot végrehajtom a lámpák eszközön a bármelyben."

## 🐛 Hibaelhárítás

### Hiba: "Nem érhető el a szerver"
**Ok:** Nincs internet vagy a szerver le van állítva

**Megoldás:**
```bash
# Szerver ellenőrzése
curl http://wisehome.hu:5000/health

# Ha nem fut, indítsd el:
docker-compose up -d
```

### Hiba: "Cleartext HTTP traffic not permitted"
**Ok:** Android 9+ blokkolja a HTTP-t

**Megoldás:** Már be van állítva az `AndroidManifest.xml`-ben:
```xml
android:usesCleartextTraffic="true"
```

### Hiba: Timeout
**Ok:** Lassú hálózat vagy szerver túlterhelés

**Megoldás:** Növeld a timeout-ot `OpenAiWorkflowService.kt`-ban.

## 🔒 Biztonsági megjegyzések

### ⚠️ Jelenlegi állapot (Development):
- HTTP (titkosítatlan)
- Nincs autentikáció
- Publikus endpoint

### ✅ Production ajánlások:
1. **HTTPS használata** - SSL certificate (Let's Encrypt)
2. **API key autentikáció** - Fejlécben API key
3. **Rate limiting** - Védelem spam ellen
4. **IP whitelist** - Csak megbízható IP-k
5. **Logging** - Minden kérés naplózása

## 💰 Költségek

**Egy interakció:**
- Whisper (5 sec): ~$0.0005
- Szerver Agent: ~$0.002
- TTS (optional): $0.00075 vagy ingyenes
- **Összesen:** ~$0.003 / használat

**Havi költség (1000 használat):**
- ~$3.00 / hó

## 📈 Teljesítmény optimalizálás

### 1. Cache-elés
Gyakori parancsok cache-elése:
```kotlin
private val cache = mutableMapOf<String, String>()
```

### 2. Párhuzamos hívások
Whisper és szerver egyszerre (ha lehetséges)

### 3. Timeout csökkentése
```kotlin
.readTimeout(60, TimeUnit.SECONDS)  // 120-ról 60-ra
```

### 4. Android TTS használata
OpenAI TTS helyett → instant válasz

## ✅ Checklist - Deployment

### Szerver (wisehome.hu):
- [x] Docker telepítve
- [x] Git repository klónozva
- [x] `.env` fájl beállítva OpenAI API kulccsal
- [x] `docker-compose up -d --build` futtatva
- [x] Port 5000 nyitva a tűzfalon
- [x] Health check működik
- [ ] HTTPS beállítása (opcionális, de ajánlott)

### Mobilapp:
- [x] `OpenAiWorkflowService.kt` frissítve
- [x] Build sikeres
- [x] APK telepítve eszközre/emulátor ra
- [x] Internet permission engedélyezve
- [x] Cleartext traffic engedélyezve
- [ ] Szerver URL tesztelve

### Tesztelés:
- [ ] Wake word működik
- [ ] Hangrögzítés működik
- [ ] Whisper átirás működik
- [ ] Szerver válaszol
- [ ] JSON parsing működik
- [ ] TTS felolvasás működik

## 🚀 Következő lépések

1. **Telepítsd az új APK-t a mobilodra**
2. **Teszteld a wisehome.hu szerverrel**
3. **Nézd a logokat mindkét oldalon**
4. **Ha működik, implementálj valódi eszközvezérlést!**

## 📞 Támogatás

Repository-k:
- **Mobilapp:** Lokális projekt
- **Szerver:** https://github.com/bogoomil/wisehome-openai-workflow

Készítve: 2024.10.12

