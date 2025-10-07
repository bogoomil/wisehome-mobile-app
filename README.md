# Voice Transcription Android App

Ez az Android alkalmazás lehetővé teszi hangrögzítést és OpenAI Whisper API-val történő transzkripciót, hasonlóan a VoiceControlClient-hez.

## Funkciók

- 🎤 Hangrögzítés egy nyomógombbal
- 🤖 OpenAI Whisper API integráció
- 📝 Magyar nyelvű transzkripció
- 🎨 Egyszerű és felhasználóbarát felület

## Előfeltételek

- Android Studio
- Android SDK 24+ (Android 7.0+)
- Internet kapcsolat (OpenAI API hozzáféréshez)

## Telepítés és futtatás

1. **Android Studio megnyitása**
   ```bash
   cd /home/kunb/CursorProjects/mobilapp
   # Android Studio-ban nyisd meg a mobilapp mappát
   ```

2. **Projekt szinkronizálása**
   - Android Studio automatikusan felismeri a Gradle projektet
   - Kattints a "Sync Now" gombra, ha megjelenik

3. **Eszköz beállítása**
   - Csatlakoztass egy Android eszközt USB-n keresztül
   - Vagy használj egy emulátort

4. **Alkalmazás futtatása**
   - Kattints a "Run" gombra (zöld play ikon)
   - Válaszd ki a céleszközt

## Használat

1. **Engedélyek**
   - Az alkalmazás első indításkor mikrofon engedélyt kér
   - Fogadd el az engedélyt a használathoz

2. **Hangrögzítés**
   - Nyomd meg a zöld "Record" gombot
   - Beszélj a mikrofonba
   - Nyomd meg a piros "Stop Recording" gombot a rögzítés leállításához

3. **Transzkripció**
   - Az alkalmazás automatikusan elküldi a hangot az OpenAI API-nak
   - A transzkripció megjelenik a szövegmezőben

## Konfiguráció

Az OpenAI API kulcsot be kell állítanod a `TranscriptionService.kt` és `OpenAiTtsService.kt` fájlokban:
```kotlin
private val apiKey = "YOUR_OPENAI_API_KEY_HERE"  // Cseréld ki a saját kulcsodra
```

**Fontos:** Soha ne commitolj API kulcsokat a verziókezelőbe!

## Technikai részletek

- **Audio formátum**: 3GP (AMR-NB kodek)
- **API**: OpenAI Whisper-1 modell
- **Nyelv**: Magyar (hu)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Függőségek

- OkHttp (hálózati kérések)
- Material Design Components
- AndroidX libraries
- Kotlin Coroutines

## Hibaelhárítás

1. **Mikrofon nem működik**
   - Ellenőrizd az engedélyeket a beállításokban
   - Győződj meg róla, hogy nincs más alkalmazás használja a mikrofont

2. **Transzkripció nem működik**
   - Ellenőrizd az internetkapcsolatot
   - Győződj meg róla, hogy az API kulcs érvényes

3. **Build hibák**
   - Töröld a `.gradle` mappát és próbáld újra
   - Ellenőrizd, hogy a Gradle wrapper megfelelően van beállítva

## Fejlesztés

A projekt struktúra:
```
mobilapp/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/voicetranscriptionapp/
│   │   │   ├── MainActivity.kt
│   │   │   └── TranscriptionService.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/strings.xml
│   │   │   └── ...
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
└── settings.gradle
```
