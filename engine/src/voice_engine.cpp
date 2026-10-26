#include "voice_engine.h"

#include <algorithm>
#include <cmath>

namespace vc {
namespace {

constexpr float kPi = static_cast<float>(M_PI);
constexpr float kTwoPi = 2.0f * kPi;

int chooseFftSize(int sampleRate) {
  // ~21 ms analysis window: long enough to resolve low male pitch,
  // short enough to keep call latency acceptable.
  return sampleRate > 24000 ? 1024 : 512;
}

int chooseLifter(int sampleRate) {
  // Keep quefrencies below the shortest expected pitch period (~600 Hz)
  // so the envelope follows formants, not harmonics.
  return std::max(12, sampleRate / 600);
}

inline float wrapPhase(float x) {
  x = std::fmod(x + kPi, kTwoPi);
  if (x < 0) x += kTwoPi;
  return x - kPi;
}

inline float softClip(float x) {
  const float a = std::fabs(x);
  if (a <= 0.9f) return x;
  const float y = 0.9f + 0.1f * std::tanh((a - 0.9f) / 0.1f);
  return x < 0 ? -y : y;
}

}  // namespace

VoiceEngine::VoiceEngine(int sampleRate)
    : sampleRate_(sampleRate),
      fftSize_(chooseFftSize(sampleRate)),
      hop_(fftSize_ / oversample_),
      lifter_(chooseLifter(sampleRate)),
      fft_(fftSize_),
      window_(fftSize_),
      inFifo_(fftSize_),
      outFifo_(fftSize_),
      outAccum_(2 * fftSize_),
      spectrum_(fftSize_),
      cepstrum_(fftSize_),
      lastPhase_(fftSize_ / 2 + 1),
      sumPhase_(fftSize_ / 2 + 1),
      anaMag_(fftSize_ / 2 + 1),
      anaFreq_(fftSize_ / 2 + 1),
      env_(fftSize_ / 2 + 1),
      synMag_(fftSize_ / 2 + 1),
      synFreq_(fftSize_ / 2 + 1),
      rover_(0),
      dryDelay_(fftSize_) {
  for (int i = 0; i < fftSize_; ++i) {
    window_[i] = 0.5f - 0.5f * std::cos(kTwoPi * i / fftSize_);
  }
  float sumSq = 0.0f;
  for (float w : window_) sumSq += w * w;
  olaGain_ = sumSq / hop_;
  reset();
}

void VoiceEngine::setParams(const VoiceParams& p) {
  pPitch_.store(std::clamp(p.pitchSemitones, -12.0f, 12.0f));
  pFormant_.store(std::clamp(p.formantRatio, 0.6f, 1.6f));
  pBright_.store(std::clamp(p.brightnessDb, -12.0f, 12.0f));
  pMix_.store(std::clamp(p.mix, 0.0f, 1.0f));
}

void VoiceEngine::reset() {
  std::fill(inFifo_.begin(), inFifo_.end(), 0.0f);
  std::fill(outFifo_.begin(), outFifo_.end(), 0.0f);
  std::fill(outAccum_.begin(), outAccum_.end(), 0.0f);
  std::fill(lastPhase_.begin(), lastPhase_.end(), 0.0f);
  std::fill(sumPhase_.begin(), sumPhase_.end(), 0.0f);
  std::fill(dryDelay_.begin(), dryDelay_.end(), 0.0f);
  rover_ = latencySamples();
  dryPos_ = 0;
  wet_ = enabled_.load() ? 1.0f : 0.0f;
}

void VoiceEngine::process(const float* in, float* out, int numSamples) {
  const int latency = latencySamples();
  const float wetTarget = enabled_.load() ? 1.0f : 0.0f;
  // ~10 ms crossfade when toggled mid-call, so switching never clicks.
  const float smooth = 1.0f / (0.01f * sampleRate_);

  for (int i = 0; i < numSamples; ++i) {
    const float x = in[i];
    inFifo_[rover_] = x;
    const float y = outFifo_[rover_ - latency];
    ++rover_;
    if (rover_ >= fftSize_) {
      rover_ = latency;
      processFrame();
    }

    // Delay the dry path by the same latency so mixing does not comb-filter.
    const float dry = dryDelay_[dryPos_];
    dryDelay_[dryPos_] = x;
    dryPos_ = (dryPos_ + 1) % latency;

    wet_ += (wetTarget - wet_) * smooth;
    const float w = wet_ * mix_;
    out[i] = softClip(dry * (1.0f - w) + y * w);
  }
}

void VoiceEngine::estimateEnvelope(const float* mag, float* env) {
  const int n = fftSize_;
  const int half = n / 2;
  for (int k = 0; k <= half; ++k) {
    cepstrum_[k] = std::log(mag[k] + 1e-6f);
  }
  for (int k = half + 1; k < n; ++k) cepstrum_[k] = cepstrum_[n - k];

  fft_.inverse(cepstrum_.data());
  const float norm = 1.0f / n;
  for (int q = 0; q < n; ++q) {
    float w;
    if (q < lifter_ || q > n - lifter_) {
      w = 1.0f;
    } else if (q == lifter_ || q == n - lifter_) {
      w = 0.5f;
    } else {
      w = 0.0f;
    }
    cepstrum_[q] = std::complex<float>(cepstrum_[q].real() * norm * w, 0.0f);
  }
  fft_.forward(cepstrum_.data());
  for (int k = 0; k <= half; ++k) {
    env[k] = std::exp(cepstrum_[k].real());
  }
}

void VoiceEngine::processFrame() {
  const int n = fftSize_;
  const int half = n / 2;
  const float expected = kTwoPi * hop_ / n;
  const float binHz = static_cast<float>(sampleRate_) / n;

  // Pick up new parameters once per frame.
  pitch_ = std::pow(2.0f, pPitch_.load() / 12.0f);
  formant_ = pFormant_.load();
  brightDb_ = pBright_.load();
  mix_ = pMix_.load();

  // --- Analysis ---
  for (int k = 0; k < n; ++k) {
    spectrum_[k] = std::complex<float>(inFifo_[k] * window_[k], 0.0f);
  }
  fft_.forward(spectrum_.data());

  for (int k = 0; k <= half; ++k) {
    const float mag = 2.0f * std::abs(spectrum_[k]);
    const float phase = std::arg(spectrum_[k]);
    float delta = phase - lastPhase_[k];
    lastPhase_[k] = phase;
    delta -= k * expected;
    delta = wrapPhase(delta);
    anaMag_[k] = mag;
    anaFreq_[k] = k + delta * oversample_ / kTwoPi;  // true frequency in bins
  }

  estimateEnvelope(anaMag_.data(), env_.data());

  // --- Pitch shift the flattened excitation ---
  std::fill(synMag_.begin(), synMag_.end(), 0.0f);
  std::fill(synFreq_.begin(), synFreq_.end(), 0.0f);
  for (int k = 0; k <= half; ++k) {
    const int idx = static_cast<int>(std::lround(k * pitch_));
    if (idx > half) break;
    synMag_[idx] += anaMag_[k] / (env_[k] + 1e-9f);
    synFreq_[idx] = anaFreq_[k] * pitch_;
  }

  // --- Re-apply warped envelope + brightness, then resynthesise ---
  const float shelfGain = std::pow(10.0f, brightDb_ / 20.0f);
  for (int k = 0; k <= half; ++k) {
    const float src = k / formant_;
    float e;
    if (src >= half) {
      e = env_[half];
    } else {
      const int i0 = static_cast<int>(src);
      const float frac = src - i0;
      e = env_[i0] * (1.0f - frac) + env_[i0 + 1] * frac;
    }
    const float hz = k * binHz;
    const float shelf = 1.0f + (shelfGain - 1.0f) / (1.0f + std::exp(-(hz - 3000.0f) / 400.0f));
    const float mag = synMag_[k] * e * shelf;

    float delta = synFreq_[k] - k;
    delta = kTwoPi * delta / oversample_;
    delta += k * expected;
    sumPhase_[k] = wrapPhase(sumPhase_[k] + delta);
    spectrum_[k] = std::polar(mag, sumPhase_[k]);
  }
  for (int k = half + 1; k < n; ++k) spectrum_[k] = 0.0f;

  fft_.inverse(spectrum_.data());

  // Undo the FFT size and the Hann^2 overlap-add gain (1.5 at 4x overlap).
  const float scale = 1.0f / (n * olaGain_);
  for (int k = 0; k < n; ++k) {
    outAccum_[k] += window_[k] * spectrum_[k].real() * scale;
  }
  for (int k = 0; k < hop_; ++k) outFifo_[k] = outAccum_[k];
  std::move(outAccum_.begin() + hop_, outAccum_.begin() + hop_ + n, outAccum_.begin());
  std::fill(outAccum_.begin() + n, outAccum_.end(), 0.0f);
  std::move(inFifo_.begin() + hop_, inFifo_.end(), inFifo_.begin());
}

}  // namespace vc
