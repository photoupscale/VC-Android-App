#pragma once

#include <cmath>
#include <complex>
#include <vector>

namespace vc {

// In-place iterative radix-2 FFT. Size must be a power of two.
class Fft {
 public:
  explicit Fft(int n) : n_(n), rev_(n), twiddle_(n / 2) {
    int bits = 0;
    while ((1 << bits) < n) ++bits;
    for (int i = 0; i < n; ++i) {
      int r = 0;
      for (int b = 0; b < bits; ++b) r |= ((i >> b) & 1) << (bits - 1 - b);
      rev_[i] = r;
    }
    for (int i = 0; i < n / 2; ++i) {
      twiddle_[i] = std::polar(1.0f, -2.0f * static_cast<float>(M_PI) * i / n);
    }
  }

  int size() const { return n_; }

  void forward(std::complex<float>* x) const { transform(x, false); }

  // Unnormalised inverse; caller divides by size().
  void inverse(std::complex<float>* x) const { transform(x, true); }

 private:
  void transform(std::complex<float>* x, bool inverse) const {
    for (int i = 0; i < n_; ++i) {
      if (i < rev_[i]) std::swap(x[i], x[rev_[i]]);
    }
    for (int len = 2; len <= n_; len <<= 1) {
      const int half = len / 2;
      const int step = n_ / len;
      for (int i = 0; i < n_; i += len) {
        for (int j = 0; j < half; ++j) {
          std::complex<float> w = twiddle_[j * step];
          if (inverse) w = std::conj(w);
          const std::complex<float> u = x[i + j];
          const std::complex<float> v = x[i + j + half] * w;
          x[i + j] = u + v;
          x[i + j + half] = u - v;
        }
      }
    }
  }

  int n_;
  std::vector<int> rev_;
  std::vector<std::complex<float>> twiddle_;
};

}  // namespace vc
