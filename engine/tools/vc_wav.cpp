// Desktop tool for tuning presets: converts a 16-bit PCM WAV file offline
// using the same engine the app runs on the phone.
//
//   vc_wav in.wav out.wav <pitchSemitones> <formantRatio> [brightnessDb] [breathiness]

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <vector>

#include "../src/voice_engine.h"

namespace {

struct Wav {
  int sampleRate = 0;
  int channels = 0;
  std::vector<float> mono;
};

bool readWav(const char* path, Wav& wav) {
  std::ifstream f(path, std::ios::binary);
  if (!f) return false;
  char riff[12];
  f.read(riff, 12);
  if (std::strncmp(riff, "RIFF", 4) != 0 || std::strncmp(riff + 8, "WAVE", 4) != 0) return false;
  int bits = 0;
  while (f) {
    char id[4];
    uint32_t size = 0;
    f.read(id, 4);
    f.read(reinterpret_cast<char*>(&size), 4);
    if (!f) break;
    if (std::strncmp(id, "fmt ", 4) == 0) {
      std::vector<char> fmt(size);
      f.read(fmt.data(), size);
      wav.channels = *reinterpret_cast<uint16_t*>(&fmt[2]);
      wav.sampleRate = static_cast<int>(*reinterpret_cast<uint32_t*>(&fmt[4]));
      bits = *reinterpret_cast<uint16_t*>(&fmt[14]);
    } else if (std::strncmp(id, "data", 4) == 0) {
      if (bits != 16 || wav.channels < 1) return false;
      std::vector<int16_t> pcm(size / 2);
      f.read(reinterpret_cast<char*>(pcm.data()), size);
      const size_t frames = pcm.size() / wav.channels;
      wav.mono.resize(frames);
      for (size_t i = 0; i < frames; ++i) {
        float sum = 0;
        for (int c = 0; c < wav.channels; ++c) sum += pcm[i * wav.channels + c];
        wav.mono[i] = sum / wav.channels / 32768.0f;
      }
      return true;
    } else {
      f.seekg(size + (size & 1), std::ios::cur);
    }
  }
  return false;
}

bool writeWav(const char* path, int sampleRate, const std::vector<float>& data) {
  std::ofstream f(path, std::ios::binary);
  if (!f) return false;
  const uint32_t dataSize = static_cast<uint32_t>(data.size() * 2);
  const uint32_t riffSize = 36 + dataSize;
  const uint16_t pcmFormat = 1, channels = 1, blockAlign = 2, bits = 16;
  const uint32_t fmtSize = 16, rate = sampleRate, byteRate = sampleRate * 2;
  f.write("RIFF", 4);
  f.write(reinterpret_cast<const char*>(&riffSize), 4);
  f.write("WAVEfmt ", 8);
  f.write(reinterpret_cast<const char*>(&fmtSize), 4);
  f.write(reinterpret_cast<const char*>(&pcmFormat), 2);
  f.write(reinterpret_cast<const char*>(&channels), 2);
  f.write(reinterpret_cast<const char*>(&rate), 4);
  f.write(reinterpret_cast<const char*>(&byteRate), 4);
  f.write(reinterpret_cast<const char*>(&blockAlign), 2);
  f.write(reinterpret_cast<const char*>(&bits), 2);
  f.write("data", 4);
  f.write(reinterpret_cast<const char*>(&dataSize), 4);
  for (float s : data) {
    const float c = s > 1.0f ? 1.0f : (s < -1.0f ? -1.0f : s);
    const int16_t v = static_cast<int16_t>(c * 32767.0f);
    f.write(reinterpret_cast<const char*>(&v), 2);
  }
  return true;
}

}  // namespace

int main(int argc, char** argv) {
  if (argc < 5) {
    std::fprintf(stderr, "usage: %s in.wav out.wav pitchSemitones formantRatio [brightnessDb] [breathiness]\n", argv[0]);
    return 2;
  }
  Wav wav;
  if (!readWav(argv[1], wav)) {
    std::fprintf(stderr, "could not read 16-bit PCM wav: %s\n", argv[1]);
    return 1;
  }
  vc::VoiceEngine engine(wav.sampleRate);
  vc::VoiceParams p;
  p.pitchSemitones = std::strtof(argv[3], nullptr);
  p.formantRatio = std::strtof(argv[4], nullptr);
  p.brightnessDb = argc > 5 ? std::strtof(argv[5], nullptr) : 0.0f;
  p.breathiness = argc > 6 ? std::strtof(argv[6], nullptr) : 0.0f;
  engine.setParams(p);
  engine.reset();

  // Feed in small blocks, like an AudioRecord callback would.
  constexpr int kBlock = 256;
  std::vector<float> out(wav.mono.size());
  for (size_t i = 0; i < wav.mono.size(); i += kBlock) {
    const int n = static_cast<int>(std::min<size_t>(kBlock, wav.mono.size() - i));
    engine.process(wav.mono.data() + i, out.data() + i, n);
  }
  if (!writeWav(argv[2], wav.sampleRate, out)) {
    std::fprintf(stderr, "could not write %s\n", argv[2]);
    return 1;
  }
  std::printf("%d Hz, %zu samples, latency %.1f ms\n", wav.sampleRate, out.size(),
              1000.0 * engine.latencySamples() / wav.sampleRate);
  return 0;
}
