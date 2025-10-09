# Voice Transcription Android App

Ez az Android alkalmazás lehetővé teszi hangrögzítést és OpenAI Whisper API-val történő transzkripciót, valamint wake word detekciót (ébresztőszó felismerés).

## Funkciók

- 🎤 Hangrögzítés egy nyomógombbal
- 🔊 Wake Word Detection - "Hello Al" ébresztőszó felismerés (Picovoice Porcupine)
- 🤖 OpenAI Whisper API integráció transzkripció
- 🗣️ OpenAI TTS (Text-to-Speech) támogatás
- 📝 Magyar nyelvű transzkripció
- 🎨 Egyszerű és felhasználóbarát felület
- 🔒 Biztonságos API key kezelés

## Előfeltételek

- Android Studio
- Android SDK 24+ (Android 7.0+)
- Internet kapcsolat (OpenAI API hozzáféréshez)
- OpenAI API kulcs ([regisztráció](https://platform.openai.com/api-keys))
- Picovoice Access Key ([regisztráció](https://console.picovoice.ai/))

## API Kulcsok beállítása

### 1. Másuld át a példa fájlt

```bash
cp local.properties.example local.properties
```

### 2. Szerezz be API kulcsokat

**OpenAI API Key:**
1. Menj a [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys) oldalra
2. Jelentkezz be vagy regisztrálj
3. Hozz létre egy új API kulcsot
4. Másold ki a kulcsot

**Picovoice Access Key:**
1. Menj a [https://console.picovoice.ai/](https://console.picovoice.ai/) oldalra
2. Regisztrálj egy ingyenes fiókot
3. Másold ki az Access Key-t a dashboard-ról

### 3. Állítsd be a kulcsokat

Nyisd meg a `local.properties` fájlt és töltsd ki az API kulcsokat:

```properties
sdk.dir=/path/to/your/Android/Sdk

# API Keys - DO NOT COMMIT!
OPENAI_API_KEY=your_openai_api_key_here
PICOVOICE_ACCESS_KEY=your_picovoice_access_key_here
```

**⚠️ FONTOS:** 
- A `local.properties` fájl `.gitignore`-ban van, **soha nem kerül verziókezelőbe**
- **NE commitold** az API kulcsokat!
- Más fejlesztők a `local.properties.example` fájlból másolhatják át a sablont

## Telepítés és futtatás

1. **Android Studio megnyitása**
   ```bash
   cd /home/kunb/CursorProjects/mobilapp
   # Android Studio-ban nyisd meg a mobilapp mappát
   ```

2. **API kulcsok beállítása**
   - Kövesd az "API Kulcsok beállítása" lépéseket fentebb

3. **Projekt szinkronizálása**
   - Android Studio automatikusan felismeri a Gradle projektet
   - Kattints a "Sync Now" gombra, ha megjelenik
   - A build rendszer beolvassa az API kulcsokat a `local.properties`-ből

4. **Eszköz beállítása**
   - Csatlakoztass egy Android eszközt USB-n keresztül
   - Vagy használj egy emulátort

5. **Alkalmazás futtatása**
   - Kattints a "Run" gombra (zöld play ikon)
   - Válaszd ki a céleszközt

## Használat

### 1. Engedélyek
- Az alkalmazás első indításkor mikrofon engedélyt kér
- Fogadd el az engedélyt a használathoz

### 2. Wake Word Mode (Ébresztőszó mód)
- Kattints a "Start Listening" gombra
- Az app folyamatosan figyel a háttérben
- Mondd: **"Hello Al"** (angolul ejtve)
- Az app automatikusan elindul és elkezd rögzíteni
- A notification tálcán látható az állapot

### 3. Manuális hangrögzítés
- Nyomd meg a zöld "Record" gombot
- Beszélj a mikrofonba
- Nyomd meg a piros "Stop Recording" gombot a rögzítés leállításához

### 4. Transzkripció
- Az alkalmazás automatikusan elküldi a hangot az OpenAI API-nak
- A transzkripció megjelenik a szövegmezőben
- A válasz hang formában is lejátszható (TTS)

## Technikai részletek

### Audio & API
- **Audio formátum**: M4A (AAC kodek)
- **Transcription API**: OpenAI Whisper-1 modell
- **TTS API**: OpenAI TTS-1 (Nova voice)
- **Wake Word Engine**: Picovoice Porcupine v3.0
- **Nyelv**: Magyar (hu)

### Android verzió
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34

### Wake Word
- **Modell**: Custom "Hello Al" wake word
- **Típus**: On-device detection (offline működés)
- **Fájl**: `Hello-Al_en_android_v3_0_0.ppn`

## Függőségek

```gradle
// Networking
- OkHttp 4.12.0
- Retrofit 2.9.0

// UI
- Material Design Components 1.10.0
- AndroidX libraries

// Coroutines
- Kotlin Coroutines 1.7.3

// Wake Word Detection
- Picovoice Porcupine 3.0.1
```

## Hibaelhárítás

### 1. Mikrofon nem működik
- Ellenőrizd az engedélyeket a beállításokban
- Győződj meg róla, hogy nincs más alkalmazás használja a mikrofont
- USB debugging engedélyezve van?

### 2. Transzkripció nem működik
- Ellenőrizd az internetkapcsolatot
- Győződj meg róla, hogy az OpenAI API kulcs érvényes
- Nézd meg a Logcat-et: szűrő = "TranscriptionService"

### 3. Wake Word nem ismer fel
- Mondd tisztán: **"Hello Al"** (angolul)
- Ellenőrizd a Picovoice Access Key-t
- Nézd meg a Logcat-et: szűrő = "WakeWordService"
- A mikrofon engedély megvan?

### 4. Build hibák - API Key hiányzik
```
Error: BuildConfig.OPENAI_API_KEY not found
```
**Megoldás:**
- Ellenőrizd, hogy létezik-e a `local.properties` fájl
- Ellenőrizd, hogy az API kulcsok megfelelően vannak beállítva
- Sync Project with Gradle Files (File → Sync Project)

### 5. Build hibák - Gradle
- Töröld a `.gradle` mappát és próbáld újra
- Build → Clean Project
- Build → Rebuild Project
- Ellenőrizd, hogy a Gradle wrapper megfelelően van beállítva

### 6. USB eszköz nem látható
- Ellenőrizd, hogy USB debugging engedélyezve van a telefonon
- Válaszd ki "File Transfer" módot az USB kapcsolatnál
- Fogadd el a "Allow USB debugging" dialógust
- Próbáld ki: `adb devices` (ha telepítve van az adb)

## Fejlesztés

### Projekt struktúra
```
mobilapp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/voicetranscriptionapp/
│   │   │   ├── MainActivity.kt              # Fő UI és koordináció
│   │   │   ├── TranscriptionService.kt     # OpenAI Whisper API
│   │   │   ├── OpenAiTtsService.kt         # OpenAI TTS API
│   │   │   ├── WakeWordService.kt          # Porcupine wake word
│   │   │   └── AndroidTtsService.kt        # Android beépített TTS
│   │   ├── assets/
│   │   │   └── Hello-Al_en_android_v3_0_0.ppn  # Wake word modell
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml    # UI layout
│   │   │   ├── values/strings.xml
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle                         # App-level build config
├── build.gradle                             # Project-level build config
├── local.properties                         # API keys (NE commitold!)
├── local.properties.example                 # Sablon más fejlesztőknek
└── README.md
```

### API Key kezelés működése

1. **local.properties** → API kulcsok tárolása (gitignore-ban)
2. **build.gradle** → Beolvassa a kulcsokat és BuildConfig-ba teszi
3. **BuildConfig** → Kotlin kódból elérhető konstansok
4. **Service osztályok** → `BuildConfig.OPENAI_API_KEY` használata

```kotlin
// Példa: OpenAiTtsService.kt
private val apiKey = BuildConfig.OPENAI_API_KEY
```

## Dokumentáció

További részletek:
- [WAKE_WORD_SETUP.md](WAKE_WORD_SETUP.md) - Wake word részletes beállítás
- [QUICK_START_WAKE_WORD.md](QUICK_START_WAKE_WORD.md) - Gyors kezdés útmutató
- [OPTIMIZATION_GUIDE.md](OPTIMIZATION_GUIDE.md) - Teljesítmény optimalizálás

## Biztonság

- ✅ API kulcsok SOHA nem kerülnek verziókezelőbe
- ✅ `local.properties` gitignore-ban van
- ✅ BuildConfig használata fordítási időben
- ✅ Nincs hardcoded API key a forráskódban
- ⚠️ Production környezetben használj backend API-t és ne tárold az API kulcsokat az app-ban!

## Licenc

Ez egy privát projekt. Minden jog fenntartva.

## Kapcsolat

Ha bármilyen kérdésed van, nyiss egy issue-t vagy contactold a fejlesztőt.
