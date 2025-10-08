# WhisperCPP Local STT Setup Guide

## 📱 Branch: `feature/whispercpp-local-stt`

Ez a branch tartalmazza a WhisperCPP lokális STT (Speech-to-Text) integrációt Android TTS-sel kombinálva, ami **teljesen offline működést** és **még gyorsabb válaszidőt** tesz lehetővé.

---

## 🎯 Új funkciók

### 1. **Dual STT Mode**
- 📱 **Local Whisper** - Offline, on-device transcription
- ☁️ **Cloud Whisper** - OpenAI API (eredeti)

### 2. **UI Controls**
- **Record gomb hosszú nyomása**: TTS mód váltás (⚡/⭐)
- **Compare gomb dupla tap**: STT mód váltás (📱/☁️)
- **Status bar**: Mutatja az aktív módokat

### 3. **Teljesítmény javítás**
```
Lokális mód (📱 Local Whisper + ⚡ Android TTS):
- Rögzítés: 2-3s
- Lokális Whisper: 1-3s (eszköz függő)
- Android TTS: <0.1s
─────────────────────────
ÖSSZ: 3-6s (teljesen offline!)
```

---

## 🛠️ Telepítési lépések

### Fázis 1: Jelenlegi állapot (Placeholder)

A jelenlegi implementáció egy **placeholder/wrapper**, ami felkészíti a projektet a WhisperCPP használatára. A tényleges natív library még nincs integrálva.

**Mit csinál most:**
- ✅ LocalWhisperService osztály létrehozva
- ✅ UI toggle-ok működnek
- ✅ Fallback cloud Whisper-re ha nincs model
- ⚠️ Placeholder transcription (nem működik élesben még)

### Fázis 2: WhisperCPP natív library integráció

#### Opció A: Whisper Android library használata (ajánlott)

1. **Adj hozzá dependency-t:**

```gradle
// app/build.gradle
dependencies {
    // WhisperCPP for Android
    implementation 'io.github.ggerganov:whisper-android:1.5.4'
    // vagy
    implementation 'com.whispercpp:whisper-android:1.0.0'
}
```

2. **Frissítsd a LocalWhisperService-t:**

```kotlin
class LocalWhisperService(private val context: Context) {
    private var whisperContext: WhisperContext? = null
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(context.filesDir, MODEL_FILENAME)
            
            if (!modelFile.exists()) {
                Log.e(TAG, "Model not found")
                return@withContext false
            }
            
            // Initialize WhisperCPP
            whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            false
        }
    }
    
    suspend fun transcribe(audioFile: File, language: String = "hu"): String {
        return whisperContext?.transcribeData(
            audioData = readAudioFile(audioFile),
            language = language
        )?.text ?: ""
    }
}
```

#### Opció B: Saját JNI binding WhisperCPP-vel

1. **Clone WhisperCPP:**
```bash
cd /home/kunb/CursorProjects/mobilapp/app/src/main/
git clone https://github.com/ggerganov/whisper.cpp.git cpp/whisper
```

2. **CMakeLists.txt létrehozása:**
```cmake
# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project("whisper_android")

add_subdirectory(whisper)

add_library(whisper_android SHARED
    whisper_jni.cpp
)

target_link_libraries(whisper_android
    whisper
    android
    log
)
```

3. **JNI wrapper írása:**
```cpp
// app/src/main/cpp/whisper_jni.cpp
#include <jni.h>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_voicetranscriptionapp_LocalWhisperService_nativeInit(
    JNIEnv* env, jobject, jstring modelPath) {
    
    const char* model_path = env->GetStringUTFChars(modelPath, 0);
    whisper_context* ctx = whisper_init_from_file(model_path);
    env->ReleaseStringUTFChars(modelPath, model_path);
    
    return (jlong)ctx;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_voicetranscriptionapp_LocalWhisperService_nativeTranscribe(
    JNIEnv* env, jobject, jlong contextPtr, jstring audioPath, jstring language) {
    
    whisper_context* ctx = (whisper_context*)contextPtr;
    // ... implement transcription ...
    
    return env->NewStringUTF(result.c_str());
}
```

4. **Uncomment CMake config build.gradle-ben:**
```gradle
android {
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }
}
```

---

## 📥 Whisper Model letöltése

### Modellek és méretek:

| Model | Méret | Sebesség | Pontosság | Ajánlott |
|-------|-------|----------|-----------|----------|
| `ggml-tiny.bin` | 75 MB | ⚡⚡⚡ | ⭐⭐ | Gyenge eszközök |
| `ggml-base.bin` | 142 MB | ⚡⚡ | ⭐⭐⭐ | ✅ **Ajánlott** |
| `ggml-small.bin` | 466 MB | ⚡ | ⭐⭐⭐⭐ | Erős eszközök |
| `ggml-medium.bin` | 1.5 GB | 🐌 | ⭐⭐⭐⭐⭐ | Csak demo |

### Letöltés:

#### 1. **HuggingFace-ről (ajánlott):**

```bash
# base model (142 MB)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

# vagy tiny model (75 MB, gyorsabb)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
```

#### 2. **Direkt link:**
- Base: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
- Tiny: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

#### 3. **Model elhelyezése Android eszközön:**

**Opció A: ADB-vel (fejlesztéshez):**
```bash
adb push ggml-base.bin /sdcard/Download/ggml-base.bin
```

Majd az alkalmazásban másold át:
```kotlin
fun copyModelToInternalStorage() {
    val downloadedModel = File("/sdcard/Download/ggml-base.bin")
    val internalModel = File(context.filesDir, "ggml-base.bin")
    
    if (downloadedModel.exists()) {
        downloadedModel.copyTo(internalModel, overwrite = true)
    }
}
```

**Opció B: Assets folder-ben (APK-ba beépítve):**
```kotlin
// Másold a modelt assets-ből internal storage-ba
fun extractModelFromAssets() {
    context.assets.open("ggml-base.bin").use { input ->
        File(context.filesDir, "ggml-base.bin").outputStream().use { output ->
            input.copyTo(output)
        }
    }
}
```

⚠️ **Figyelem:** APK méret jelentősen megnő (+142 MB)!

**Opció C: Runtime letöltés (production ajánlott):**
```kotlin
suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
    val url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    val outputFile = File(context.filesDir, "ggml-base.bin")
    
    try {
        // Download with progress
        URL(url).openStream().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Model download failed", e)
        false
    }
}
```

---

## 🎮 Használati útmutató

### STT Mód váltás:

1. **Dupla tap a Compare gombon**
   - 📱 Local Whisper (Offline) ↔️ ☁️ Cloud Whisper (Online)

2. **Status bar mutatja az aktív módot:**
   - `STT: Local (Offline)` - Lokális Whisper
   - `STT: Cloud (Online)` - OpenAI API

### TTS Mód váltás:

1. **Hosszú nyomás a Record gombon**
   - ⚡ Fast (Android TTS) ↔️ ⭐ Quality (OpenAI TTS)

### Optimális kombináció:

```
🚀 Leggyorsabb (teljesen offline):
   📱 Local Whisper + ⚡ Android TTS
   └─ Várható idő: 3-6s

💎 Legjobb minőség:
   ☁️ Cloud Whisper + ⭐ OpenAI TTS
   └─ Várható idő: 5-8s

⚖️ Kiegyensúlyozott:
   📱 Local Whisper + ⭐ OpenAI TTS
   └─ Várható idő: 4-7s
```

---

## 📊 Teljesítmény összehasonlítás

### Branch-ek összehasonlítása:

| Branch | STT | TTS | Idő | Offline | APK méret |
|--------|-----|-----|-----|---------|-----------|
| **master** | Cloud | OpenAI | 7-12s | ❌ | ~8 MB |
| **master** (opt) | Cloud | Android | 3-5s | ❌ | ~8 MB |
| **feature/whispercpp** | Local | Android | 3-6s | ✅ | ~150 MB* |
| **feature/whispercpp** | Local | OpenAI | 4-7s | ⚠️ | ~150 MB* |

*APK méret model nélkül + model SD kártyán/letöltve

### Eszköz specifikus teljesítmény:

**Modern eszköz (Snapdragon 8 Gen 2+):**
```
Lokális Whisper (base): 1-2s
Várható összes idő: 3-5s
```

**Közepes eszköz (Snapdragon 7 Gen 1):**
```
Lokális Whisper (base): 2-4s
Várható összes idő: 4-7s
```

**Gyenge eszköz (Snapdragon 6 Gen 1):**
```
Lokális Whisper (base): 4-8s
Várható összes idő: 6-11s
└─ Ajánlott: tiny model használata (1-3s)
```

---

## 🔧 Fejlesztői jegyzetek

### Jelenlegi állapot:

```kotlin
// LocalWhisperService.kt
// - ✅ Wrapper class kész
// - ✅ Initialize, transcribe, release methods
// - ⚠️ Native binding még nincs implementálva
// - ⚠️ Audio preprocessing placeholder

// MainActivity.kt
// - ✅ Dual mode toggle (Local/Cloud)
// - ✅ UI indicators
// - ✅ Fallback logic
// - ✅ Performance timing
```

### Következő lépések (production):

1. **WhisperCPP library integráció**
   - Válaszd az Opció A vagy B-t fentről
   - Implementáld a JNI bindings-t
   
2. **Audio preprocessing**
   - 16kHz mono WAV konverzió
   - FFmpeg vagy MediaCodec használata
   
3. **Model management**
   - Runtime letöltés implementálása
   - Progress indicator
   - Model verzió kezelés
   
4. **Optimalizációk**
   - GPU gyorsítás (ha támogatott)
   - Quantized modellek
   - Model caching

---

## ⚠️ Ismert korlátozások

### Jelenlegi branch:

1. **Placeholder transcription**
   - LocalWhisperService még nem végez tényleges átírást
   - Fallback cloud Whisper-re működik

2. **Model nincs mellék

elve**
   - Külön le kell tölteni
   - Manuális telepítés szükséges

3. **Nincs audio preprocessing**
   - Feltételezi, hogy a felvétel megfelelő formátumú
   - Production-ben konverzió szükséges

### Általános korlátozások:

1. **Nagy APK méret**
   - Model nélkül: ~8-10 MB
   - Model-lel (assets): ~150-500 MB
   - Megoldás: Runtime letöltés

2. **Eszköz erőforrás igényes**
   - CPU intensive
   - Akkumulátor fogyasztás
   - Lassabb eszközökön lassú lehet

3. **Memória használat**
   - Model betöltése: ~400-800 MB RAM
   - Alacsony RAM eszközökön crash veszély

---

## 🚀 Gyors start (testing)

### 1. Checkout branch:
```bash
git checkout feature/whispercpp-local-stt
```

### 2. Build APK:
```bash
./gradlew assembleDebug
```

### 3. Install:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Tesztelés:
- App indítása
- Ha nincs model: ☁️ Cloud mode aktív (működik)
- Ha van model: 📱 Local mode aktív (placeholder)

---

## 📚 További források

### WhisperCPP:
- **GitHub:** https://github.com/ggerganov/whisper.cpp
- **Android példa:** https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android
- **Modellek:** https://huggingface.co/ggerganov/whisper.cpp

### Alternatívák:
- **whisper-android (library):** https://github.com/MahmoudAshraf97/whisper-android
- **ONNX Runtime:** https://onnxruntime.ai/
- **TensorFlow Lite:** https://www.tensorflow.org/lite

### Android ML:
- **Neural Networks API:** https://developer.android.com/ndk/guides/neuralnetworks
- **ML Kit:** https://developers.google.com/ml-kit

---

## 💬 Összefoglalás

Ez a branch **felkészíti a projektet** a WhisperCPP integrációra, de még nem tartalmazza a tényleges natív implementációt.

**Előnyök:**
- ✅ Teljesen offline működés (amikor kész)
- ✅ Gyorsabb lehet (eszköz függő)
- ✅ Nincs API költség
- ✅ Adatvédelem (minden eszközön marad)

**Hátrányok:**
- ❌ Nagy APK/storage méret
- ❌ Eszköz függő teljesítmény
- ❌ Komplexebb telepítés
- ❌ Natív kód karbantartás

**Mikor használd:**
- Offline működés szükséges
- Sok felhasználó → API költség magas
- Adatvédelem kritikus
- Erős céleszközök

**Mikor NE használd:**
- Gyenge céleszközök
- APK méret kritikus
- Cloud már gyors elég
- Nincs C++ tudás a csapatban

---

## 🎯 Következő lépés

Ha komolyan szeretnéd használni a lokális Whisper-t, akkor:

1. **Próbáld ki az Opció A-t** (külső library)
2. **Töltsd le a base modelt** (142 MB)
3. **Teszteld eszközön** a teljesítményt
4. **Döntsd el**, hogy megéri-e

Vagy maradj a **master branch**-nél az optimalizált cloud megoldással! 😊

