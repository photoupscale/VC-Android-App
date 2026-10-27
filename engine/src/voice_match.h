#pragma once

#include <array>
#include <vector>

namespace vc {

// Log-spaced frequency grid used to describe a voice's average vocal-tract
// envelope, independent of sample rate.
constexpr int kProfilePoints = 128;
constexpr float kProfileMinHz = 80.0f;
constexpr float kProfileMaxHz = 7000.0f;

// Tone-matching EQ bands applied by the engine (log-spaced centres).
constexpr int kEqBands = 24;
constexpr float kEqMinHz = 100.0f;
constexpr float kEqMaxHz = 8000.0f;

float profileHz(int i);
float eqBandHz(int band);

struct VoiceProfile {
  // Median fundamental frequency of voiced speech; 0 if too little speech was found.
  float medianF0 = 0.0f;
  // Seconds of voiced speech the profile was built from.
  float voicedSeconds = 0.0f;
  // Average spectral envelope in dB at profileHz(i), mean-normalised.
  std::array<float, kProfilePoints> envelopeDb{};

  bool valid() const { return medianF0 > 0.0f && voicedSeconds >= 1.0f; }
};

// Builds a profile from mono speech at any sample rate. Uses at most
// maxVoicedSeconds of voiced speech to bound the work on long recordings.
VoiceProfile analyzeVoice(const float* samples, int count, int sampleRate, float maxVoicedSeconds = 30.0f);

struct VoiceMatch {
  float pitchSemitones = 0.0f;
  float formantRatio = 1.0f;
  float breathiness = 0.0f;
  std::array<float, kEqBands> eqDb{};
};

// Engine settings that make `source` (your voice) sound like `target`.
VoiceMatch matchVoices(const VoiceProfile& source, const VoiceProfile& target);

}  // namespace vc
