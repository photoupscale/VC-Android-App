// Checks that voice matching recovers known pitch and vocal-tract differences
// between two synthetic voices, and that the engine's EQ path stays stable.

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <random>
#include <vector>

#include "../src/voice_engine.h"
#include "../src/voice_match.h"

namespace {

int failures = 0;

void check(bool ok, const char* what) {
  std::printf("%s %s\n", ok ? "PASS" : "FAIL", what);
  if (!ok) ++failures;
}

// Vowel-like envelope: three formant resonances, all scaled by `tract`.
float envelope(float hz, float tract) {
  const float formants[3] = {600.0f, 1700.0f, 2700.0f};
  const float widths[3] = {120.0f, 180.0f, 250.0f};
  float a = 0.02f;
  for (int i = 0; i < 3; ++i) {
    const float d = (hz - formants[i] * tract) / (widths[i] * tract);
    a += std::exp(-0.5f * d * d) / (i + 1);
  }
  return a;
}

// Synthetic speech: harmonics shaped by the envelope, slight vibrato, short pauses.
std::vector<float> speak(int sr, float f0, float tract, float seconds, unsigned seed) {
  std::mt19937 rng(seed);
  std::normal_distribution<float> jitter(0.0f, 0.002f);
  std::vector<float> out(static_cast<size_t>(sr * seconds));
  double phase = 0;
  for (size_t i = 0; i < out.size(); ++i) {
    const float t = static_cast<float>(i) / sr;
    const bool pause = std::fmod(t, 1.0f) > 0.85f;
    const float f = f0 * (1.0f + 0.02f * std::sin(2 * M_PI * 5.0 * t) + jitter(rng));
    phase += 2 * M_PI * f / sr;
    if (pause) continue;
    float v = 0;
    for (int h = 1; h * f < 7500.0f; ++h) v += envelope(h * f, tract) * std::sin(h * phase);
    out[i] = 0.1f * v;
  }
  return out;
}

}  // namespace

int main() {
  char msg[200];
  for (int sr : {16000, 44100, 48000}) {
    const auto male = speak(sr, 115.0f, 1.0f, 6.0f, 1);
    const auto female = speak(sr, 215.0f, 1.17f, 6.0f, 2);
    const auto pm = vc::analyzeVoice(male.data(), static_cast<int>(male.size()), sr);
    const auto pf = vc::analyzeVoice(female.data(), static_cast<int>(female.size()), sr);

    std::snprintf(msg, sizeof msg, "%d Hz: male F0 ~115 Hz (got %.1f, %.1f s voiced)", sr, pm.medianF0, pm.voicedSeconds);
    check(pm.valid() && std::fabs(pm.medianF0 - 115.0f) < 6.0f, msg);
    std::snprintf(msg, sizeof msg, "%d Hz: female F0 ~215 Hz (got %.1f)", sr, pf.medianF0);
    check(pf.valid() && std::fabs(pf.medianF0 - 215.0f) < 10.0f, msg);

    const auto m = vc::matchVoices(pm, pf);
    const float expectedPitch = 12.0f * std::log2(215.0f / 115.0f);
    std::snprintf(msg, sizeof msg, "%d Hz: male->female pitch %+.1f st (expected %+.1f)", sr, m.pitchSemitones, expectedPitch);
    check(std::fabs(m.pitchSemitones - expectedPitch) < 1.0f, msg);
    std::snprintf(msg, sizeof msg, "%d Hz: male->female formant x%.3f (expected ~1.17)", sr, m.formantRatio);
    check(std::fabs(m.formantRatio - 1.17f) < 0.06f, msg);

    const auto back = vc::matchVoices(pf, pm);
    std::snprintf(msg, sizeof msg, "%d Hz: female->male formant x%.3f (expected ~0.85)", sr, back.formantRatio);
    check(std::fabs(back.formantRatio - 1.0f / 1.17f) < 0.06f, msg);

    float maxEq = 0;
    for (float g : m.eqDb) maxEq = std::max(maxEq, std::fabs(g));
    std::snprintf(msg, sizeof msg, "%d Hz: EQ stays gentle (max %.1f dB)", sr, maxEq);
    check(maxEq <= 9.0f, msg);

    // Run the engine with the matched settings and EQ; output must stay bounded.
    vc::VoiceEngine engine(sr);
    vc::VoiceParams p;
    p.pitchSemitones = m.pitchSemitones;
    p.formantRatio = m.formantRatio;
    p.breathiness = m.breathiness;
    engine.setParams(p);
    engine.setEq(m.eqDb.data());
    engine.reset();
    std::vector<float> out(male.size());
    for (size_t i = 0; i < male.size(); i += 256) {
      const int n = static_cast<int>(std::min<size_t>(256, male.size() - i));
      engine.process(male.data() + i, out.data() + i, n);
    }
    bool finite = true;
    for (float v : out) finite = finite && std::isfinite(v) && std::fabs(v) <= 1.0f;
    const auto converted = vc::analyzeVoice(out.data(), static_cast<int>(out.size()), sr);
    std::snprintf(msg, sizeof msg, "%d Hz: converted male lands near female F0 (got %.1f Hz)", sr, converted.medianF0);
    check(finite && std::fabs(converted.medianF0 - 215.0f) < 15.0f, msg);
  }

  std::vector<float> silence(48000 * 3, 0.0f);
  const auto empty = vc::analyzeVoice(silence.data(), static_cast<int>(silence.size()), 48000);
  check(!empty.valid(), "silence gives an invalid profile instead of a bogus voice");
  return failures == 0 ? 0 : 1;
}
