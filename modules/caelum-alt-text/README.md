# On-device ALT tools

Android-only local Expo module used by `src/features/altTextGeneration` and the
image ALT editor. Requires a native rebuild and Android API 26 or newer. Expo
autolinking discovers `expo-module.config.json` in the local modules directory.
`app.config.js` sets the minimum SDK through `expo-build-properties`.

- **OCR** uses the bundled ML Kit Latin-script text recognizer, without a model
  download. It reads visible text, not scenes. Other scripts are not included.
- **Describe** uses the beta ML Kit Image Description API and AICore. Its button
  stays hidden until runtime support is confirmed when opening the ALT editor,
  with a recheck when returning to the app. Unsupported or failed checks leave
  OCR available. Downloadable/downloading models count as supported; merely
  checking support never starts a download. The UI asks before initiating or
  joining a model download and shows progress when Google provides the total size. Output is
  currently English. There is no cloud fallback.
- Results are reviewable drafts. They only enter the existing ALT input when
  the author chooses Use text, Replace ALT text, or Append. Existing ALT length
  warnings and save-time limits still apply.

Inputs must be local `file://`, `content://`, or absolute paths. The module bounds
decoded image dimensions and applies EXIF orientation. It uses the composer's
transformed image when present. Image contents and output are never logged.

Only one generation operation runs at a time. Cancel/close invalidates the result
and progress immediately. Google does not guarantee physical cancellation of
ongoing inference/downloads; the native operation may finish in the background
before releasing its bitmap/client. A subsequent request can return `ERR_BUSY`
until that work completes. The module checks cancellation before starting
inference and discards late results. Model downloads are shared and managed by
AICore.

Validation: `pnpm test --runInBand
src/features/altTextGeneration/useAltTextGeneration.test.ts`, `pnpm lint`,
`pnpm typecheck`, and an Android build. Real description output, model downloads,
and offline generation require a supported physical device; an emulator cannot
validate them. Test cropped/rotated photos, text screenshots, empty images, manual
edits during generation, dismiss/reopen, and unavailable/low-storage/busy states.

Before public distribution, resolve the GenAI age/distribution requirements
identified in [the feasibility memo](../../docs/mlkit-on-device-alt-text.md).
This implementation does not resolve those terms or constitute a release.

Sources: [ML Kit OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android),
[Image Description](https://developers.google.com/ml-kit/genai/image-description/android),
[GenAI terms](https://developers.google.com/ml-kit/genai-terms),
[SDK data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
