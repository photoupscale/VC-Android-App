// Sanity checks for the DSP engine: pitch lands where expected, output level
// stays close to input, and the engine stays stable on silence and noise.

#include <cmath>
#include <complex>
#include <cstdio>
#include <random>
#include <vector>

#include "../src/fft.h"
#include "../src/voice_engine.h"

namespace {

int failures = 0;

void check(bool ok, const char* what) {
  std::printf("%s %s\n", ok ? "PASS" : "FAIL", what);
  if (!ok) ++failures;
}

float rms(const std::vector<float>& x, size_t from) {
  double s = 0;
  for (size_t i = from; i < x.size(); ++i) s += x[i] * x[i];
  return static_cast<float>(std::sqrt(s / (x.size() - from)));
}

float dominantHz(const std::vector<float>& x, size_t from, int sr) {
  const int n = 8192;
  vc::Fft fft(n);
  std::vector<std::complex<float>> buf(n);
  for (int i = 0; i < n; ++i) {
    const float w = 0.5f - 0.5f * std::cos(2 * M_PI * i / n);
    buf[i] = x[from + i] * w;
  }
  fft.forward(buf.data());
  int best = 1;
  for (int k = 1; k < n / 2; ++k) {
    if (std::abs(buf[k]) > std::abs(buf[best])) best = k;
  }
  return static_cast<float>(best) * sr / n;
}

std::vector<float> run(int sr, const std::vector<float>& in, vc::VoiceParams p) {
  vc::VoiceEngine e(sr);
  e.setParams(p);
  e.reset();
  std::vector<float> out(in.size());
  for (size_t i = 0; i < in.size(); i += 160) {
    const int n = static_cast<int>(std::min<size_t>(160, in.size() - i));
    e.process(in.data() + i, out.data() + i, n);
  }
  return out;
}

}  // namespace

int main() {
  for (int sr : {16000, 48000}) {
    std::vector<float> sine(sr * 2);
    for (size_t i = 0; i < sine.size(); ++i) sine[i] = 0.3f * std::sin(2 * M_PI * 200.0 * i / sr);

    vc::VoiceParams up;
    up.pitchSemitones = 12;
    up.formantRatio = 1.0f;
    auto out = run(sr, sine, up);
    const float hz = dominantHz(out, sr / 2, sr);
    char msg[128];
    std::snprintf(msg, sizeof msg, "%d Hz: +12 st moves 200 Hz to ~400 Hz (got %.1f)", sr, hz);
    check(std::fabs(hz - 400.0f) < 8.0f, msg);

    vc::VoiceParams down;
    down.pitchSemitones = -5;
    down.formantRatio = 0.87f;
    out = run(sr, sine, down);
    const float hzDown = dominantHz(out, sr / 2, sr);
    const float expected = 200.0f * std::pow(2.0f, -5.0f / 12.0f);
    std::snprintf(msg, sizeof msg, "%d Hz: -5 st moves 200 Hz to ~%.0f Hz (got %.1f)", sr, expected, hzDown);
    check(std::fabs(hzDown - expected) < 8.0f, msg);

    vc::VoiceParams flat;
    out = run(sr, sine, flat);
    const float ratio = rms(out, sr / 2) / rms(sine, sr / 2);
    std::snprintf(msg, sizeof msg, "%d Hz: level preserved at 0 st (ratio %.2f)", sr, ratio);
    check(ratio > 0.7f && ratio < 1.3f, msg);

    std::mt19937 rng(1);
    std::normal_distribution<float> nd(0.0f, 0.1f);
    std::vector<float> noise(sr);
    for (auto& v : noise) v = nd(rng);
    out = run(sr, noise, up);
    bool finite = true;
    for (float v : out) finite = finite && std::isfinite(v) && std::fabs(v) <= 1.0f;
    std::snprintf(msg, sizeof msg, "%d Hz: stable and bounded on noise", sr);
    check(finite, msg);

    std::vector<float> silence(sr, 0.0f);
    out = run(sr, silence, up);
    std::snprintf(msg, sizeof msg, "%d Hz: silence stays silent", sr);
    check(rms(out, 0) < 1e-4f, msg);
  }
  return failures == 0 ? 0 : 1;
}
