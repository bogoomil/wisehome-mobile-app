# Wake Word Detection Setup Guide

## 🎯 Overview

Ez a branch implementálja a wake word (ébresz szó) detektálást Porcupine by Picovoice használatával.

### Működés:
```
1. App indítás
   ↓
2. "Start Listening" gomb → WakeWordService indul
   ↓
3. 🎤 Mikrofon folyamatosan hallgat (foreground service)
   ↓
4. Felhasználó mondja: "Porcupine" (vagy más beállított wake word)
   ↓
5. ✅ Wake word érzékelve!
   ↓
6. 🔴 Automatikus rögzítés indul (5 másodperc)
   ↓
7. ☁️ Cloud Whisper transcription
   ↓
8. 🔊 TTS válasz
   ↓
9. 🔄 Vissza listening módba
```

---

## 🔑 Porcupine Access Key megszerzése

### Lépések:

1. **Regisztráció**
   - Menj ide: https://console.picovoice.ai/
   - Regisztrálj ingyenes fiókot

2. **Access Key másolása**
   - A Dashboard-on látod az Access Key-t
   - Másold ki

3. **Access Key beállítása**
   ```kotlin
   // WakeWordService.kt
   private const val ACCESS_KEY = "YOUR_KEY_HERE" // ← Ide másold
   ```

### Ingyenes tier:
- ✅ 1 wake word
- ✅ Korlátlan használat
- ✅ Minden platform
- ✅ Nincs hitelkártya szükséges

---

## 🎙️ Elérhető Wake Words

### Beépített wake words (angolul):

```kotlin
// WakeWordService.kt - 83. sor
Porcupine.BuiltInKeyword.PORCUPINE  // ← Alapértelmezett
```

**Választható wake words:**
- `ALEXA` - "Alexa"
- `AMERICANO` - "Americano"  
- `BLUEBERRY` - "Blueberry"
- `BUMBLEBEE` - "Bumblebee"
- `COMPUTER` - "Computer" ⭐ Ajánlott
- `GRAPEFRUIT` - "Grapefruit"
- `GRASSHOPPER` - "Grasshopper"
- `HEY_GOOGLE` - "Hey Google"
- `HEY_SIRI` - "Hey Siri"
- `JARVIS` - "Jarvis" ⭐ Ajánlott
- `OK_GOOGLE` - "Ok Google"
- `PICOVOICE` - "Picovoice"
- `PORCUPINE` - "Porcupine" (default)
- `TERMINATOR` - "Terminator"

### Wake word módosítása:

```kotlin
// WakeWordService.kt
porcupineManager = PorcupineManager.Builder()
    .setAccessKey(ACCESS_KEY)
    .setKeyword(Porcupine.BuiltInKeyword.COMPUTER) // ← Változtasd meg
    .build(applicationContext, callback)
```

### Custom wake word (Pro verzió):
- Létrehozhatsz saját wake word-öt
- Picovoice Console-ban
- Bármilyen szó/kifejezés

---

## 📱 Használat

### 1. Alapértelmezett mód (kézi rögzítés):
- Nyomd meg a Record gombot
- Beszélj
- Nyomd meg újra

### 2. Wake Word mód (hands-free):
1. **Nyomd meg "Start Wake Word" gombot**
   - Notification jelenik meg: "🎤 Listening... Say 'Porcupine'"
   - Mikrofon folyamatosan hallgat

2. **Mondd a wake word-öt:**
   - "Porcupine" (vagy beállított szó)
   - Rövid beep hang (opcionális)
   - Automatikusan rögzít 5 másodpercig

3. **Beszélj a kérdésed/parancsod:**
   - A rögzítés automatikus
   - Átírás és válasz automatikus

4. **Ismétlés:**
   - Mondd újra a wake word-öt
   - Folyamatos használat

5. **Leállítás:**
   - "Stop Wake Word" gomb
   - Vagy notification "Stop" gombja

---

## ⚙️ MainActivity integráció

### Új gombok:

```xml
<!-- Hozzáadandó a layout-hoz -->
<Button
    android:id="@+id/wakeWordToggleButton"
    android:text="Start Wake Word"
    ... />
```

### BroadcastReceiver:

```kotlin
private val wakeWordReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WakeWordService.ACTION_WAKE_WORD_DETECTED -> {
                // Automatikus rögzítés indítása
                startAutoRecording()
            }
        }
    }
}
```

### Auto recording:

```kotlin
private fun startAutoRecording() {
    lifecycleScope.launch {
        // Vizuális feedback
        binding.statusText.text = "✅ Wake word detected! Recording..."
        
        // Rögzítés indítása
        startRecording()
        
        // Auto-stop 5 másodperc után
        delay(5000)
        stopRecording()
    }
}
```

---

## 🔋 Akkumulátor optimalizálás

### Porcupine akkumulátor használat:
- **Folyamatos hallgatás:** ~1-2% / óra
- **Optimalizált:** Nagyon alacsony CPU használat
- **Beépített:** Batch processing, sleep modes

### Best practices:

1. **Stop when not needed:**
   ```kotlin
   override fun onPause() {
       super.onPause()
       // Stop wake word service when app is in background (optional)
       WakeWordService.stopService(this)
   }
   ```

2. **Battery-aware mode:**
   ```kotlin
   val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
   val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
   
   if (batteryLevel < 20) {
       // Stop wake word detection on low battery
       WakeWordService.stopService(this)
       Toast.makeText(this, "Wake word stopped due to low battery", Toast.LENGTH_LONG).show()
   }
   ```

3. **User preference:**
   ```kotlin
   // Beállítások: "Keep listening when screen off"
   // Alapértelmezett: STOP when screen off
   ```

---

## 🎨 UI/UX javaslatok

### Status indicators:
```
🎤 Listening...        ← Wake word mode aktív
✅ Wake word detected! ← Érzékelve
🔴 Recording...        ← Rögzítés folyamatban
☁️ Transcribing...    ← Átírás
🔊 Speaking...         ← TTS válasz
```

### Colors:
- **Zöld:** Listening mode
- **Narancs:** Wake word detected
- **Piros:** Recording
- **Kék:** Processing

### Animations (optional):
- Pulse animation a Record gombon listening közben
- Ripple effect wake word detection-kor

---

## ⚠️ Ismert korlátozások

### 1. **Access Key szükséges**
- Ingyenes regisztráció
- Internet szükséges első setup-hoz
- Key beégetve az app-ba (ne commitold publikusan!)

### 2. **Csak angol wake words alapból**
- Magyar wake word: Custom (Pro verzió)
- Vagy használj angol szót ("Computer", "Jarvis")

### 3. **Mikrofonhatótávolság:**
- Optimális: 1-3 méter
- Háttérzaj érzékenység: közepes
- Nagyon zajos környezetben kevésbé pontos

### 4. **Foreground service:**
- Notification mindig látszik
- Android 12+ kötelező
- Nem rejection-re ítélt Play Store-ban

---

## 🧪 Tesztelési checklist

- [ ] Access Key beállítva
- [ ] Wake word érzékelése működik
- [ ] Automatikus rögzítés indul
- [ ] Transcription működik
- [ ] TTS válasz működik
- [ ] Visszatér listening módba
- [ ] Notification megjelenik
- [ ] Stop gomb működik
- [ ] App háttérben is működik
- [ ] Akkumulátor használat elfogadható
- [ ] Mikrofonhatótávolság tesztelve
- [ ] Zajös környezetben tesztelve

---

## 🚀 Következő lépések

### Fejlesztési ötletek:

1. **Beállítások képernyő:**
   - Wake word kiválasztása
   - Érzékenység beállítása
   - Auto-stop timer (5/10/15 sec)
   - Battery-aware mode

2. **Több wake word:**
   ```kotlin
   // Több wake word egyszerre
   setKeywords(
       Porcupine.BuiltInKeyword.COMPUTER,
       Porcupine.BuiltInKeyword.JARVIS
   )
   ```

3. **Custom feedback:**
   - Beep hang wake word detection-kor
   - Vibráció
   - LED flash

4. **Conversation mode:**
   - Folyamatos beszélgetés
   - Nem kell újra wake word
   - Timeout után vissza wake word mode-ba

5. **Context awareness:**
   - "Continue" command detection
   - Multi-turn conversation
   - History tracking

---

## 📚 Dokumentáció

### Porcupine dokumentáció:
- Hivatalos docs: https://picovoice.ai/docs/porcupine/
- Android integration: https://github.com/Picovoice/porcupine/tree/master/binding/android
- Console: https://console.picovoice.ai/

### API Reference:
- https://picovoice.ai/docs/api/porcupine-android/

---

## 💡 Tippek

1. **Első használatkor:**
   - Mondd tisztán a wake word-öt
   - Ne túl hangosan, ne túl halkan
   - Várj a beep-re vagy vizuális jelzésre

2. **Optimális környezet:**
   - Csendes környezet (első teszthez)
   - Közeli mikrofon (1-2 méter)
   - Tiszta kiejtés

3. **Ha nem működik:**
   - Ellenőrizd az Access Key-t
   - Nézd a Logcat-et: "WhisperJNI" vagy "WakeWordService"
   - Mikrofon engedély megadva?
   - Notification permission (Android 13+)?

---

## 🎉 Összefoglalás

A wake word detection hands-free használatot tesz lehetővé:
- ✅ Nem kell gomb nyomkodás
- ✅ Természetes interakció
- ✅ Folyamatos hallgatás
- ✅ Alacsony akkumulátor használat
- ✅ Háttérben is működik

**Élmény:**
"Computer" → beep → "Mi az időjárás?" → "A jelenlegi hőmérséklet 15 fok..."

