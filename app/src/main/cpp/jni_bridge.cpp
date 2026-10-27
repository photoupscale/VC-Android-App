// JNI bridge between Kotlin (app UI and the LSPosed hook) and the C++ engine.
// Handles the PCM layouts AudioRecord can hand us: 16-bit or float, mono or
// interleaved multi-channel, in Java arrays or direct ByteBuffers.

#include <jni.h>

#include <cstdint>
#include <cstring>
#include <memory>
#include <vector>

#include "voice_engine.h"
#include "voice_match.h"

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

JNIEXPORT void JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeSetEq(JNIEnv* env, jclass, jlong h, jfloatArray gainsDb) {
  if (gainsDb == nullptr || env->GetArrayLength(gainsDb) < vc::kEqBands) {
    fromLong(h)->engine.setEq(nullptr);
    return;
  }
  float db[vc::kEqBands];
  env->GetFloatArrayRegion(gainsDb, 0, vc::kEqBands, db);
  fromLong(h)->engine.setEq(db);
}

// Returns [medianF0, voicedSeconds, envelopeDb x kProfilePoints].
JNIEXPORT jfloatArray JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeAnalyze(JNIEnv* env, jclass, jshortArray pcm, jint length,
                                                           jint sampleRate) {
  std::vector<float> samples(length);
  jshort* data = env->GetShortArrayElements(pcm, nullptr);
  if (data == nullptr) return nullptr;
  for (int i = 0; i < length; ++i) samples[i] = data[i] / 32768.0f;
  env->ReleaseShortArrayElements(pcm, data, JNI_ABORT);

  const vc::VoiceProfile p = vc::analyzeVoice(samples.data(), length, sampleRate);
  float out[2 + vc::kProfilePoints];
  out[0] = p.medianF0;
  out[1] = p.voicedSeconds;
  std::memcpy(out + 2, p.envelopeDb.data(), sizeof(float) * vc::kProfilePoints);
  jfloatArray result = env->NewFloatArray(2 + vc::kProfilePoints);
  env->SetFloatArrayRegion(result, 0, 2 + vc::kProfilePoints, out);
  return result;
}

// Takes two arrays from nativeAnalyze; returns [pitch, formant, breathiness, eqDb x kEqBands].
JNIEXPORT jfloatArray JNICALL
Java_com_voicechanger_app_engine_NativeBridge_nativeMatch(JNIEnv* env, jclass, jfloatArray source, jfloatArray target) {
  auto toProfile = [env](jfloatArray a) {
    vc::VoiceProfile p;
    if (a == nullptr || env->GetArrayLength(a) < 2 + vc::kProfilePoints) return p;
    float buf[2 + vc::kProfilePoints];
    env->GetFloatArrayRegion(a, 0, 2 + vc::kProfilePoints, buf);
    p.medianF0 = buf[0];
    p.voicedSeconds = buf[1];
    std::memcpy(p.envelopeDb.data(), buf + 2, sizeof(float) * vc::kProfilePoints);
    return p;
  };
  const vc::VoiceMatch m = vc::matchVoices(toProfile(source), toProfile(target));
  float out[3 + vc::kEqBands];
  out[0] = m.pitchSemitones;
  out[1] = m.formantRatio;
  out[2] = m.breathiness;
  std::memcpy(out + 3, m.eqDb.data(), sizeof(float) * vc::kEqBands);
  jfloatArray result = env->NewFloatArray(3 + vc::kEqBands);
  env->SetFloatArrayRegion(result, 0, 3 + vc::kEqBands, out);
  return result;
}

}  // extern "C"
