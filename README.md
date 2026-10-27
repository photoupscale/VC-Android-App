# Voice Changer

Real-time male ↔ female voice changer for WhatsApp and Messenger calls on Android,
plus a no-root voice-note mode.

## How it works

| Mode | Needs | What it does |
|---|---|---|
| **Live calls** | Rooted phone with Magisk + LSPosed | An LSPosed module hooks `AudioRecord.read()` inside WhatsApp / Messenger and converts each microphone buffer before the app sends it. |
| **Voice notes** | Any phone | Record a message in the changed voice and share it to WhatsApp or Messenger as audio. |

Android does not let normal apps change the microphone of other apps, so live
call conversion is only possible with root.

### Voice engine

`engine/` is a small C++ library shared by both modes:

- Phase-vocoder pitch shift (~16 ms latency at 48 kHz, ~24 ms at 16 kHz).
- Per-frame spectral envelope via cepstral liftering, so formants (the size of the
  vocal tract) are kept or moved independently of pitch. Moving both together is
  what makes a male → female change sound like a person instead of a chipmunk.
- High-shelf brightness control, smooth on/off crossfade, soft limiter.
- Breathiness: envelope-shaped aspiration noise above ~1.5 kHz, which makes shifted
  male voices sound much less buzzy and more like a real female voice.
- Real-time safe: no allocation or locks in the audio path.

Presets live in `app/src/main/java/com/voicechanger/app/engine/VoicePresets.kt`.

## Features

- 24 male → female voices, from deep alto to light soprano, each with its own pitch,
  formant, brightness and breathiness
- 8 female → male voices
- Fun: Child, Giant, Chipmunk, Monster
- Custom pitch / formant / brightness / breathiness sliders
- **My voices**: add a voice from a recording of someone (audio file, WhatsApp voice
  note, video, or recorded live). The app measures the target's pitch, vocal-tract
  size and tone against a 10 s sample of your own voice and saves matching settings,
  including a 24-band tone EQ. This approximates the voice; it is not neural cloning.
- **Auto-activate in background**: converts every call without opening the app.
  Turn it off for manual mode and use the "Voice changer" Quick Settings tile per call.
- Per-app choice: WhatsApp, WhatsApp Business, Messenger, Facebook
- Live preview with headphones

## Install (live calls)

1. Root the phone with Magisk and install LSPosed (Zygisk version).
2. Install the APK from the latest GitHub Actions build or release.
3. In LSPosed → Modules, enable **Voice Changer** and tick WhatsApp, Messenger and Voice Changer itself.
4. Force-stop WhatsApp / Messenger, then open Voice Changer. The status card should say "Ready for live calls".

## Build

```sh
./gradlew assembleRelease          # APK in app/build/outputs/apk/release/
```

Engine tests and an offline WAV tool for tuning presets:

```sh
cmake -S engine -B build/engine && cmake --build build/engine
ctest --test-dir build/engine
build/engine/vc_wav in.wav out.wav 5 1.16 1.5 0.18   # pitch semitones, formant ratio, brightness dB, breathiness
```

## Known limits

- If a future WhatsApp/Messenger version moves its mic capture to native AAudio/OpenSL,
  the Java `AudioRecord` hook will stop seeing the audio and a native hook will be needed.
- Large shifts (more than ~8 semitones) start to sound processed. The next step for
  more realism is a streaming neural model (LLVC / StreamVC style via ONNX Runtime)
  for the "realistic" presets, with this DSP engine as the fallback.
- Sideload only. Play Store does not allow apps that modify other apps.

Use responsibly: impersonating someone to deceive or defraud is illegal in most places.
