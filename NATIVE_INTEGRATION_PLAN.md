# WhisperCPP Native Integration Plan

## 🎯 Cél
Működő natív WhisperCPP integráció Android-ra, amely ténylegesen végez offline transcription-t.

---

## 📋 Implementációs lépések

### Fázis 1: Whisper.cpp beszerzése és konfiguráció

#### Opció A: Git Submodule (ajánlott fejlesztéshez)
```bash
cd /home/kunb/CursorProjects/mobilapp
git submodule add https://github.com/ggerganov/whisper.cpp.git app/src/main/cpp/whisper
cd app/src/main/cpp/whisper
git checkout master
```

#### Opció B: Csak a szükséges fájlok másolása (egyszerűbb)
```bash
# Klónozás ideiglenes helyre
cd /tmp
git clone https://github.com/ggerganov/whisper.cpp.git
cd whisper.cpp

# Másoljuk a szükséges fájlokat
mkdir -p /home/kunb/CursorProjects/mobilapp/app/src/main/cpp/whisper
cp whisper.h whisper.cpp ggml.h ggml.c ggml-alloc.h ggml-alloc.c ggml-backend.h ggml-backend.c \
   /home/kunb/CursorProjects/mobilapp/app/src/main/cpp/whisper/
```

---

### Fázis 2: CMakeLists.txt létrehozása

```cmake
# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project(whisper_android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")

# Whisper library
add_library(whisper STATIC
    whisper/whisper.cpp
    whisper/ggml.c
    whisper/ggml-alloc.c
    whisper/ggml-backend.c
)

target_include_directories(whisper PUBLIC whisper)

# JNI wrapper
add_library(whisper_android SHARED
    whisper_jni.cpp
)

target_link_libraries(whisper_android
    whisper
    android
    log
)
```

---

### Fázis 3: JNI Wrapper implementáció

```cpp
// app/src/main/cpp/whisper_jni.cpp
#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper/whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_voicetranscriptionapp_LocalWhisperService_nativeInit(
    JNIEnv* env, jobject, jstring modelPath) {
    
    const char* model_path = env->GetStringUTFChars(modelPath, nullptr);
    LOGD("Initializing Whisper with model: %s", model_path);
    
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context* ctx = whisper_init_from_file_with_params(model_path, cparams);
    
    env->ReleaseStringUTFChars(modelPath, model_path);
    
    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context");
        return 0;
    }
    
    LOGD("Whisper context initialized successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_example_voicetranscriptionapp_LocalWhisperService_nativeTranscribe(
    JNIEnv* env, jobject, jlong contextPtr, jstring audioPath, jstring language) {
    
    auto* ctx = reinterpret_cast<struct whisper_context*>(contextPtr);
    if (ctx == nullptr) {
        LOGE("Invalid whisper context");
        return env->NewStringUTF("");
    }
    
    const char* audio_path = env->GetStringUTFChars(audioPath, nullptr);
    const char* lang = env->GetStringUTFChars(language, nullptr);
    
    LOGD("Transcribing: %s (language: %s)", audio_path, lang);
    
    // Read WAV file
    std::vector<float> pcmf32;
    if (!read_wav(audio_path, pcmf32)) {
        LOGE("Failed to read WAV file");
        env->ReleaseStringUTFChars(audioPath, audio_path);
        env->ReleaseStringUTFChars(language, lang);
        return env->NewStringUTF("");
    }
    
    // Whisper parameters
    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.language = lang;
    wparams.n_threads = 4; // Use 4 threads
    wparams.offset_ms = 0;
    wparams.duration_ms = 0;
    
    // Run transcription
    int result = whisper_full(ctx, wparams, pcmf32.data(), pcmf32.size());
    
    env->ReleaseStringUTFChars(audioPath, audio_path);
    env->ReleaseStringUTFChars(language, lang);
    
    if (result != 0) {
        LOGE("Transcription failed");
        return env->NewStringUTF("");
    }
    
    // Get transcription text
    const int n_segments = whisper_full_n_segments(ctx);
    std::string text;
    
    for (int i = 0; i < n_segments; ++i) {
        const char* segment_text = whisper_full_get_segment_text(ctx, i);
        text += segment_text;
    }
    
    LOGD("Transcription complete: %s", text.c_str());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_voicetranscriptionapp_LocalWhisperService_nativeRelease(
    JNIEnv*, jobject, jlong contextPtr) {
    
    auto* ctx = reinterpret_cast<struct whisper_context*>(contextPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGD("Whisper context released");
    }
}

// Helper function to read WAV file
bool read_wav(const char* fname, std::vector<float>& pcmf32) {
    // WAV file reading implementation
    // This is a simplified version - you'll need proper WAV parsing
    FILE* f = fopen(fname, "rb");
    if (f == nullptr) {
        LOGE("Failed to open file: %s", fname);
        return false;
    }
    
    // Skip WAV header (44 bytes)
    fseek(f, 44, SEEK_SET);
    
    // Read PCM data
    std::vector<int16_t> pcm16;
    int16_t sample;
    while (fread(&sample, sizeof(int16_t), 1, f) == 1) {
        pcm16.push_back(sample);
    }
    fclose(f);
    
    // Convert to float
    pcmf32.resize(pcm16.size());
    for (size_t i = 0; i < pcm16.size(); i++) {
        pcmf32[i] = float(pcm16[i]) / 32768.0f;
    }
    
    LOGD("Read %zu samples from WAV file", pcmf32.size());
    return true;
}

} // extern "C"
```

---

### Fázis 4: Audio Preprocessing (M4A → WAV)

Két megoldás:

#### Opció A: MediaCodec (Android beépített)
```kotlin
class AudioConverter(private val context: Context) {
    
    fun convertM4AtoWAV(inputFile: File, outputFile: File): Boolean {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)
            
            // Find audio track
            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrack = i
                    break
                }
            }
            
            if (audioTrack == -1) return false
            
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            
            // Create decoder
            val mime = format.getString(MediaFormat.KEY_MIME)
            val decoder = MediaCodec.createDecoderByType(mime!!)
            decoder.configure(format, null, null, 0)
            decoder.start()
            
            // Decode to PCM
            val pcmData = mutableListOf<Short>()
            // ... decoding logic ...
            
            // Write WAV file
            writeWAVFile(outputFile, pcmData, 16000, 1)
            
            decoder.stop()
            decoder.release()
            extractor.release()
            
            return true
        } catch (e: Exception) {
            Log.e("AudioConverter", "Conversion failed", e)
            return false
        }
    }
    
    private fun writeWAVFile(
        file: File, 
        pcmData: List<Short>, 
        sampleRate: Int, 
        channels: Int
    ) {
        val outputStream = FileOutputStream(file)
        val dataSize = pcmData.size * 2 // 16-bit = 2 bytes per sample
        
        // WAV header
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToBytes(36 + dataSize))
        outputStream.write("WAVE".toByteArray())
        
        // fmt chunk
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToBytes(16)) // chunk size
        outputStream.write(shortToBytes(1)) // audio format (PCM)
        outputStream.write(shortToBytes(channels.toShort()))
        outputStream.write(intToBytes(sampleRate))
        outputStream.write(intToBytes(sampleRate * channels * 2)) // byte rate
        outputStream.write(shortToBytes((channels * 2).toShort())) // block align
        outputStream.write(shortToBytes(16)) // bits per sample
        
        // data chunk
        outputStream.write("data".toByteArray())
        outputStream.write(intToBytes(dataSize))
        
        // PCM data
        for (sample in pcmData) {
            outputStream.write(shortToBytes(sample))
        }
        
        outputStream.close()
    }
    
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
}
```

#### Opció B: FFmpeg library (külső dependency)
```gradle
dependencies {
    implementation 'com.arthenica:ffmpeg-kit-full:5.1'
}
```

```kotlin
import com.arthenica.ffmpegkit.FFmpegKit

class AudioConverter {
    fun convertM4AtoWAV(inputFile: File, outputFile: File): Boolean {
        val command = "-i ${inputFile.absolutePath} -ar 16000 -ac 1 -f wav ${outputFile.absolutePath}"
        val session = FFmpegKit.execute(command)
        return session.returnCode.isValueSuccess
    }
}
```

---

### Fázis 5: LocalWhisperService frissítése

```kotlin
class LocalWhisperService(private val context: Context) {
    
    private var whisperContext: Long = 0L
    private val audioConverter = AudioConverter(context)
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDownloader = ModelDownloader(context)
            
            val modelFile = when {
                modelDownloader.isModelDownloaded(ModelDownloader.Companion.ModelType.BASE) -> {
                    modelDownloader.getModelFile(ModelDownloader.Companion.ModelType.BASE)
                }
                modelDownloader.isModelDownloaded(ModelDownloader.Companion.ModelType.TINY) -> {
                    modelDownloader.getModelFile(ModelDownloader.Companion.ModelType.TINY)
                }
                else -> {
                    Log.e(TAG, "No Whisper model found")
                    return@withContext false
                }
            }
            
            modelPath = modelFile.absolutePath
            
            // Initialize native Whisper context
            whisperContext = nativeInit(modelPath!!)
            isInitialized = (whisperContext != 0L)
            
            if (isInitialized) {
                Log.i(TAG, "Native Whisper initialized: $modelPath")
            }
            
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper", e)
            false
        }
    }
    
    suspend fun transcribe(audioFile: File, language: String = "hu"): String {
        if (!isInitialized) {
            Log.e(TAG, "Whisper not initialized")
            return ""
        }
        
        return withContext(Dispatchers.Default) {
            try {
                // Convert M4A to WAV
                val wavFile = File.createTempFile("whisper_", ".wav", context.cacheDir)
                val converted = audioConverter.convertM4AtoWAV(audioFile, wavFile)
                
                if (!converted) {
                    Log.e(TAG, "Audio conversion failed")
                    return@withContext ""
                }
                
                // Native transcription
                val result = nativeTranscribe(whisperContext, wavFile.absolutePath, language)
                
                // Cleanup
                wavFile.delete()
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                ""
            }
        }
    }
    
    fun release() {
        if (whisperContext != 0L) {
            nativeRelease(whisperContext)
            whisperContext = 0L
        }
        isInitialized = false
    }
    
    // Native methods
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(context: Long, audioPath: String, language: String): String
    private external fun nativeRelease(context: Long)
    
    companion object {
        init {
            System.loadLibrary("whisper_android")
        }
    }
}
```

---

### Fázis 6: build.gradle frissítése

```gradle
android {
    // ...
    
    defaultConfig {
        // ...
        
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'
        }
        
        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17 -O3"
                arguments "-DANDROID_STL=c++_shared"
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path file('src/main/cpp/CMakeLists.txt')
            version '3.22.1'
        }
    }
}

dependencies {
    // ... existing dependencies ...
    
    // FFmpeg (if using FFmpeg for audio conversion)
    // implementation 'com.arthenica:ffmpeg-kit-audio:5.1'
}
```

---

## ⚠️ Fontos megjegyzések

### Komplexitás:
- **C++ kód**: Whisper natív library
- **JNI bindings**: Java ↔ C++ kommunikáció
- **Audio konverzió**: M4A → WAV
- **Build konfiguráció**: CMake, NDK

### Build idő:
- Első build: **5-10 perc**
- Újabb build-ek: 1-2 perc

### APK méret:
- Native libs: +10-15 MB (architecture függő)
- Total APK: ~18-23 MB (model nélkül)

### Tesztelés:
- Csak ARM eszközökön működik (Android >= 7.0)
- Emulator: csak x86_64 variant-tal

---

## 🚀 Gyors start (lépésről lépésre)

1. **Whisper.cpp letöltése**
2. **CMakeLists.txt létrehozása**
3. **JNI wrapper írása**
4. **Audio converter implementálása**
5. **LocalWhisperService frissítése**
6. **Build és teszt**

Minden lépést külön commit-ban dokumentálunk.

---

## 📊 Várható teljesítmény

| Eszköz | Model | Idő (10s audio) |
|--------|-------|-----------------|
| Snapdragon 8 Gen 2 | TINY | 1-2s |
| Snapdragon 8 Gen 2 | BASE | 2-4s |
| Snapdragon 7 Gen 1 | TINY | 2-3s |
| Snapdragon 7 Gen 1 | BASE | 4-7s |

---

## 💡 Következő lépés

Válassz implementációs módot:
- **A**: Teljes native integráció (2-3 nap munka)
- **B**: Külső library használata (1 nap munka)  
- **C**: Csak audio converter (partial implementation)

Melyikkel folytassuk?

