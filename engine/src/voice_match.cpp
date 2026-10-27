#include "voice_match.h"

#include <algorithm>
#include <cmath>
#include <complex>

#include "fft.h"

namespace vc {
namespace {

constexpr int kAnalysisRate = 16000;
constexpr int kFrame = 512;  // 32 ms
constexpr int kHop = 160;    // 10 ms
constexpr int kMinLag = kAnalysisRate / 500;  // 500 Hz
constexpr int kMaxLag = kAnalysisRate / 70;   // 70 Hz
constexpr float kVoicedCorrelation = 0.6f;
constexpr float kDbPerNeper = 8.685889638f;   // 20 / ln(10)

std::vector<float> resampleTo16k(const float* x, int count, int sampleRate) {
  if (sampleRate == kAnalysisRate) return std::vector<float>(x, x + count);
  // Box filter against aliasing when downsampling, then linear interpolation.
  const int box = std::max(1, static_cast<int>(std::ceil(static_cast<float>(sampleRate) / kAnalysisRate)));
  std::vector<float> smoothed(count);
  float acc = 0.0f;
  for (int i = 0; i < count; ++i) {
    acc += x[i];
    if (i >= box) acc -= x[i - box];
    smoothed[i] = acc / std::min(i + 1, box);
  }
  const double step = static_cast<double>(sampleRate) / kAnalysisRate;
  const int outCount = static_cast<int>((count - 1) / step);
  std::vector<float> out(std::max(0, outCount));
  for (int i = 0; i < outCount; ++i) {
    const double pos = i * step;
    const int i0 = static_cast<int>(pos);
    const float frac = static_cast<float>(pos - i0);
    out[i] = smoothed[i0] * (1.0f - frac) + smoothed[std::min(i0 + 1, count - 1)] * frac;
  }
  return out;
}

// Normalised cross-correlation pitch estimate; returns 0 for unvoiced frames.
float estimateF0(const float* x) {
  float best = 0.0f;
  int bestLag = 0;
  std::array<float, kMaxLag + 2> r{};
  for (int lag = kMinLag; lag <= kMaxLag + 1; ++lag) {
    double xy = 0, xx = 0, yy = 0;
    for (int n = 0; n + lag < kFrame; ++n) {
      xy += x[n] * x[n + lag];
      xx += x[n] * x[n];
      yy += x[n + lag] * x[n + lag];
    }
    r[lag] = (xx > 0 && yy > 0) ? static_cast<float>(xy / std::sqrt(xx * yy)) : 0.0f;
    if (lag <= kMaxLag && r[lag] > best) {
      best = r[lag];
      bestLag = lag;
    }
  }
  if (best < kVoicedCorrelation) return 0.0f;
  // Avoid octave-down errors: take the shortest lag that is nearly as good.
  for (int lag = kMinLag; lag < bestLag; ++lag) {
    if (r[lag] > 0.9f * best && r[lag] >= r[lag - 1] && r[lag] >= r[lag + 1]) {
      bestLag = lag;
      break;
    }
  }
  // Parabolic refinement.
  float lag = static_cast<float>(bestLag);
  if (bestLag > kMinLag && bestLag < kMaxLag) {
    const float a = r[bestLag - 1], b = r[bestLag], c = r[bestLag + 1];
    const float denom = a - 2 * b + c;
    if (std::fabs(denom) > 1e-9f) lag += 0.5f * (a - c) / denom;
  }
  return kAnalysisRate / lag;
}

float interpolateProfile(const std::array<float, kProfilePoints>& env, float hz) {
  const float pos = std::log(hz / kProfileMinHz) / std::log(kProfileMaxHz / kProfileMinHz) * (kProfilePoints - 1);
  if (pos <= 0) return env[0];
  if (pos >= kProfilePoints - 1) return env[kProfilePoints - 1];
  const int i0 = static_cast<int>(pos);
  const float frac = pos - i0;
  return env[i0] * (1.0f - frac) + env[i0 + 1] * frac;
}

}  // namespace

float profileHz(int i) {
  return kProfileMinHz * std::pow(kProfileMaxHz / kProfileMinHz, static_cast<float>(i) / (kProfilePoints - 1));
}

float eqBandHz(int band) {
  return kEqMinHz * std::pow(kEqMaxHz / kEqMinHz, static_cast<float>(band) / (kEqBands - 1));
}

VoiceProfile analyzeVoice(const float* samples, int count, int sampleRate, float maxVoicedSeconds) {
  VoiceProfile profile;
  if (count <= 0 || sampleRate <= 0) return profile;
  const std::vector<float> x = resampleTo16k(samples, count, sampleRate);
  const int frames = (static_cast<int>(x.size()) - kFrame) / kHop;
  if (frames <= 0) return profile;

  // Ignore quiet frames (pauses, background) relative to the loud speech.
  std::vector<float> energy(frames);
  for (int f = 0; f < frames; ++f) {
    double e = 0;
    for (int n = 0; n < kFrame; ++n) e += x[f * kHop + n] * x[f * kHop + n];
    energy[f] = static_cast<float>(std::sqrt(e / kFrame));
  }
  std::vector<float> sorted = energy;
  std::nth_element(sorted.begin(), sorted.begin() + frames * 95 / 100, sorted.end());
  const float gate = std::max(1e-4f, 0.15f * sorted[frames * 95 / 100]);

  Fft fft(kFrame);
  std::vector<std::complex<float>> buf(kFrame);
  std::vector<float> window(kFrame);
  for (int n = 0; n < kFrame; ++n) window[n] = 0.5f - 0.5f * std::cos(2.0f * static_cast<float>(M_PI) * n / kFrame);
  const int lifter = kAnalysisRate / 600;
  const float binHz = static_cast<float>(kAnalysisRate) / kFrame;

  std::vector<float> f0s;
  std::array<double, kProfilePoints> envSum{};
  const int maxVoicedFrames = static_cast<int>(maxVoicedSeconds * kAnalysisRate / kHop);

  for (int f = 0; f < frames && static_cast<int>(f0s.size()) < maxVoicedFrames; ++f) {
    if (energy[f] < gate) continue;
    const float* frame = &x[f * kHop];
    const float f0 = estimateF0(frame);
    if (f0 <= 0.0f) continue;
    f0s.push_back(f0);

    // Cepstral envelope, same method the real-time engine uses.
    for (int n = 0; n < kFrame; ++n) buf[n] = frame[n] * window[n];
    fft.forward(buf.data());
    for (int k = 0; k < kFrame; ++k) buf[k] = std::log(std::abs(buf[k]) + 1e-6f);
    fft.inverse(buf.data());
    for (int q = 0; q < kFrame; ++q) {
      const bool keep = q < lifter || q > kFrame - lifter;
      buf[q] = keep ? buf[q].real() / kFrame : 0.0f;
    }
    fft.forward(buf.data());
    for (int i = 0; i < kProfilePoints; ++i) {
      const float bin = profileHz(i) / binHz;
      const int b0 = std::min(static_cast<int>(bin), kFrame / 2 - 1);
      const float frac = bin - b0;
      const float logMag = buf[b0].real() * (1.0f - frac) + buf[b0 + 1].real() * frac;
      envSum[i] += logMag * kDbPerNeper;
    }
  }
  if (f0s.empty()) return profile;

  std::nth_element(f0s.begin(), f0s.begin() + f0s.size() / 2, f0s.end());
  profile.medianF0 = f0s[f0s.size() / 2];
  profile.voicedSeconds = static_cast<float>(f0s.size()) * kHop / kAnalysisRate;
  double mean = 0;
  for (int i = 0; i < kProfilePoints; ++i) {
    profile.envelopeDb[i] = static_cast<float>(envSum[i] / f0s.size());
    mean += profile.envelopeDb[i];
  }
  mean /= kProfilePoints;
  for (auto& v : profile.envelopeDb) v -= static_cast<float>(mean);
  return profile;
}

VoiceMatch matchVoices(const VoiceProfile& source, const VoiceProfile& target) {
  VoiceMatch m;
  if (!source.valid() || !target.valid()) return m;

  m.pitchSemitones = std::clamp(12.0f * std::log2(target.medianF0 / source.medianF0), -12.0f, 12.0f);

  // Find the vocal-tract scaling that best lines up the two envelopes over the
  // formant region, ignoring overall level.
  auto residualCost = [&](float ratio, float* offset) {
    double sum = 0, sumSq = 0;
    int n = 0;
    for (int i = 0; i < kProfilePoints; ++i) {
      const float hz = profileHz(i);
      if (hz < 200.0f || hz > 5000.0f) continue;
      const float d = target.envelopeDb[i] - interpolateProfile(source.envelopeDb, hz / ratio);
      sum += d;
      sumSq += d * d;
      ++n;
    }
    const double mean = sum / n;
    if (offset) *offset = static_cast<float>(mean);
    return sumSq / n - mean * mean;
  };
  double bestCost = 1e30;
  for (float ratio = 0.75f; ratio <= 1.35f; ratio += 0.005f) {
    const double cost = residualCost(ratio, nullptr);
    if (cost < bestCost) {
      bestCost = cost;
      m.formantRatio = ratio;
    }
  }

  // Whatever the scaling can't explain becomes a gentle tone-matching EQ.
  float offset = 0.0f;
  residualCost(m.formantRatio, &offset);
  std::array<float, kEqBands> raw{};
  const float halfBand = 0.5f * std::log(kEqMaxHz / kEqMinHz) / (kEqBands - 1);
  for (int b = 0; b < kEqBands; ++b) {
    const float centre = std::log(eqBandHz(b));
    double sum = 0;
    int n = 0;
    for (int i = 0; i < kProfilePoints; ++i) {
      const float hz = profileHz(i);
      if (std::fabs(std::log(hz) - centre) > halfBand) continue;
      sum += target.envelopeDb[i] - interpolateProfile(source.envelopeDb, hz / m.formantRatio) - offset;
      ++n;
    }
    raw[b] = n > 0 ? static_cast<float>(sum / n) : 0.0f;
  }
  for (int b = 0; b < kEqBands; ++b) {
    const float prev = raw[std::max(b - 1, 0)], next = raw[std::min(b + 1, kEqBands - 1)];
    m.eqDb[b] = std::clamp(0.25f * prev + 0.5f * raw[b] + 0.25f * next, -9.0f, 9.0f);
  }

  // Shifting a voice up leaves it buzzier than a natural higher voice; add a little air.
  m.breathiness = m.pitchSemitones > 2.0f ? 0.15f : 0.0f;
  return m;
}

}  // namespace vc
