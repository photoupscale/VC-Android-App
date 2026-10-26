// JNI bridge between Kotlin (app UI and the LSPosed hook) and the C++ engine.
// Handles the PCM layouts AudioRecord can hand us: 16-bit or float, mono or
// interleaved multi-channel, in Java arrays or direct ByteBuffers.

#include <jni.h>

#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>

#include "voice_engine.h"

namespace {

constexpr int kEncodingPcm16 = 2;  // AudioFormat.ENCODING_PCM_16BIT
constexpr int kEncodingFloat = 4;  // AudioFormat.ENCODING_PCM_FLOAT

struct Handle {
  explicit Handle(int sampleRate) : engine(sampleRate) {}
  vc::VoiceEngine engine;
  std::vector<float> scratch;
};

Handle* fromLong(jlong h) { return reinterpret_cast<Handle*>(h); }

float* ensureScratch(Handle* h, int frames) {
  if (static_cast<int>(h->scratch.size()) < frames) h->scratch.resize(frames);
  return h->scratch.data();
}

void processPcm16(Handle* h, int16_t* data, int samples, int channels) {
  if (channels < 1) channels = 1;
  const int frames = samples / channels;
  float* buf = ensureScratch(h, frames);
  for (int i = 0; i < frames; ++i) {
    int sum = 0;
    for (int c = 0; c < channels; ++c) sum += data[i * channels + c];
    buf[i] = static_cast<float>(sum) / channels / 32768.0f;
  }
  h->engine.process(buf, buf, frames);
  for (int i = 0; i < frames; ++i) {
    float v = buf[i] * 32767.0f;
    if (v > 32767.0f) v = 32767.0f;
    if (v < -32768.0f) v = -32768.0f;
    const auto s = static_cast<int16_t>(v);
    for (int c = 0; c < channels; ++c) data[i * channels + c] = s;
  }
}

void processFloat(Handle* h, float* data, int samples, int channels) {
  if (channels < 1) channels = 1;
  const int frames = samples / channels;
  float* buf = ensureScratch(h, frames);
  for (int i = 0; i < frames; ++i) {
    float sum = 0.0f;
    for (int c = 0; c < channels; ++c) sum += data[i * channels + c];
    buf[i] = sum / channels;
  }
  h->engine.process(buf, buf, frames);
  for (int i = 0; i < frames; ++i) {
    for (int c = 0; c < channels; ++c) data[i * channels + c] = buf[i];
  }
}

void processRaw(Handle* h, uint8_t* bytes, int lengthBytes, int channels, int encoding) {
  if (encoding == kEncodingFloat) {
    processFloat(h, reinterpret_cast<float*>(bytes), lengthBytes / 4, channels);
  } else if (encoding == kEncodingPcm16) {
    processPcm16(h, reinterpret_cast<int16_t*>(bytes), lengthBytes / 2, channels);
  }
  // 8-bit and compressed encodings are passed through untouched.
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeCreate(JNIEnv*, jclass, jint sampleRate) {
  return reinterpret_cast<jlong>(new Handle(sampleRate));
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeDestroy(JNIEnv*, jclass, jlong h) {
  delete fromLong(h);
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeSetParams(JNIEnv*, jclass, jlong h, jfloat pitch,
                                                              jfloat formant, jfloat brightness, jfloat breathiness,
                                                              jfloat mix) {
  vc::VoiceParams p;
  p.pitchSemitones = pitch;
  p.formantRatio = formant;
  p.brightnessDb = brightness;
  p.breathiness = breathiness;
  p.mix = mix;
  fromLong(h)->engine.setParams(p);
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeSetEnabled(JNIEnv*, jclass, jlong h, jboolean enabled) {
  fromLong(h)->engine.setEnabled(enabled);
}

JNIEXPORT jint JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeLatencyMs(JNIEnv*, jclass, jlong h) {
  const auto& e = fromLong(h)->engine;
  return 1000 * e.latencySamples() / e.sampleRate();
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeProcessShorts(JNIEnv* env, jclass, jlong h, jshortArray array,
                                                                  jint offset, jint length, jint channels) {
  jshort* data = env->GetShortArrayElements(array, nullptr);
  if (data == nullptr) return;
  processPcm16(fromLong(h), reinterpret_cast<int16_t*>(data + offset), length, channels);
  env->ReleaseShortArrayElements(array, data, 0);
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeProcessFloats(JNIEnv* env, jclass, jlong h, jfloatArray array,
                                                                  jint offset, jint length, jint channels) {
  jfloat* data = env->GetFloatArrayElements(array, nullptr);
  if (data == nullptr) return;
  processFloat(fromLong(h), data + offset, length, channels);
  env->ReleaseFloatArrayElements(array, data, 0);
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeProcessBytes(JNIEnv* env, jclass, jlong h, jbyteArray array,
                                                                 jint offset, jint lengthBytes, jint channels,
                                                                 jint encoding) {
  jbyte* data = env->GetByteArrayElements(array, nullptr);
  if (data == nullptr) return;
  processRaw(fromLong(h), reinterpret_cast<uint8_t*>(data + offset), lengthBytes, channels, encoding);
  env->ReleaseByteArrayElements(array, data, 0);
}

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeProcessDirect(JNIEnv* env, jclass, jlong h, jobject buffer,
                                                                  jint offsetBytes, jint lengthBytes, jint channels,
                                                                  jint encoding) {
  auto* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
  if (data == nullptr) return;
  processRaw(fromLong(h), data + offsetBytes, lengthBytes, channels, encoding);
}

}  // extern "C"
