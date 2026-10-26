#pragma once

#include <atomic>
#include <complex>
#include <vector>

#include "fft.h"

namespace vc {

struct VoiceParams {
  // Pitch shift in semitones. Male->female is typically +4..+7, female->male -4..-7.
  float pitchSemitones = 0.0f;
  // Formant (vocal tract) scaling. >1 sounds smaller/female, <1 larger/male.
  float formantRatio = 1.0f;
  // High-shelf tilt above ~3 kHz in dB. Female voices usually sit slightly brighter.
  float brightnessDb = 0.0f;
  // Dry/wet mix, 1 = fully converted.
  float mix = 1.0f;
};

// Streaming mono voice converter: phase-vocoder pitch shift with spectral
// envelope (formant) preservation and independent formant warping.
//
// The envelope is estimated per frame by cepstral liftering. The excitation
// (envelope-flattened spectrum) is pitch-shifted, then re-coloured with the
// envelope warped by formantRatio. Keeping pitch and formants decoupled is
// what avoids the "chipmunk" / robotic sound of a plain pitch shift.
//
// process() is real-time safe: no allocation, no locks.
class VoiceEngine {
 public:
  explicit VoiceEngine(int sampleRate);

  // Thread-safe; picked up at the next analysis frame.
  void setParams(const VoiceParams& p);
  void setEnabled(bool enabled) { enabled_.store(enabled); }
  bool enabled() const { return enabled_.load(); }

  // in and out may alias.
  void process(const float* in, float* out, int numSamples);

  void reset();

  int sampleRate() const { return sampleRate_; }
  int latencySamples() const { return fftSize_ - hop_; }

 private:
  void processFrame();
  void estimateEnvelope(const float* mag, float* env);

  const int sampleRate_;
  const int fftSize_;
  const int oversample_ = 4;
  const int hop_;
  const int lifter_;

  Fft fft_;
  std::vector<float> window_;
  float olaGain_ = 1.0f;
  std::vector<float> inFifo_;
  std::vector<float> outFifo_;
  std::vector<float> outAccum_;
  std::vector<std::complex<float>> spectrum_;
  std::vector<std::complex<float>> cepstrum_;
  std::vector<float> lastPhase_;
  std::vector<float> sumPhase_;
  std::vector<float> anaMag_;
  std::vector<float> anaFreq_;
  std::vector<float> env_;
  std::vector<float> synMag_;
  std::vector<float> synFreq_;
  int rover_;

  // Pending params written by the UI thread, current params used by audio thread.
  std::atomic<float> pPitch_{0.0f}, pFormant_{1.0f}, pBright_{0.0f}, pMix_{1.0f};
  float pitch_ = 1.0f, formant_ = 1.0f, brightDb_ = 0.0f, mix_ = 1.0f;
  std::atomic<bool> enabled_{true};
  float wet_ = 1.0f;  // smoothed enable crossfade
  std::vector<float> dryDelay_;
  int dryPos_ = 0;
};

}  // namespace vc
