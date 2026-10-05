# Moe Memos 2.1.0-beta.2 feature and verification matrix

This document records the behavior accepted for the KawhiCurry release candidate
on 2026-10-05. It is a verification snapshot, not a list of planned work.

## Release identity

- Release application ID: `online.kawhicurry.moememos`.
- Debug application ID: `online.kawhicurry.moememos.spacepreview`.
- Kotlin/Android namespace: `me.mudkip.moememos` (kept unchanged so existing
  source packages and component class names remain valid).
- Version: `2.1.0-beta.2` (`versionCode` 52).
- The dedicated application ID and signing key keep this fork isolated from the
  upstream Play/F-Droid package.

## Validated targets

- Device: Pixel 10a, Android 17 / API 37, arm64-v8a.
- Local account: Room database and app-private attachment storage, with no server
  dependency.
- Remote account: Memos 0.31.0 at `https://memos.kawhicurry.online`, private
  instance mode.
- Server path: HTTPS uploads, including a 3 MiB test image and mixed media, pass
  through the configured reverse proxy. The canonical instance URL is set.

## Accepted behavior

| Area | Accepted result |
| --- | --- |
| Local and remote CRUD | Create, edit, delete, search, pin/unpin, archive/restore and account switching were exercised on the Pixel. Test artifacts were removed afterward. |
| Memos 0.31 API | PATCH requests generate the required field-specific `updateMask`; SPACE visibility, paginated resources, comments and reactions use the 0.31 contracts. |
| Offline sync | A memo created behind an unreachable proxy remained locally marked unsynced. Reconnect created exactly one remote memo, a repeated sync did not duplicate it, and cleanup restored the baseline. Network failure is reported as sync failure rather than unsupported server version. |
| Upload resilience | Attachments have stable client identities, 256 KiB chunk upload support, retry/recovery for response loss, and a narrow compatibility fallback. |
| Mixed media | Text, PNG, MP4 and recorded M4A coexist in one local or remote memo. Remote audio and video were downloaded and played in Android media apps. |
| Speech input | Android's standard recognizer Activity remains the preferred path. If a ROM exposes only a `RecognitionService`, the editor requests microphone permission and falls back to the in-process `SpeechRecognizer`; no Google package name is required. Cancellation, lifecycle cleanup, cursor insertion and distinct permission/network/language/no-match errors are covered. |
| Voice attachment | The editor records AAC/M4A, finalizes recording on lifecycle stop or duration limit, blocks submission while recording, and supports local/remote playback. |
| Social interaction | Own-timeline and Explore cards load Memos comments and reactions. Expanded state reloads details exactly once after state restoration. |
| QQ-Space-style home | Responsive cover/profile/stat header, four quick actions, rich cards and light/dark layouts were exercised on phone width. |
| Quick-capture widget | Text, voice, camera and media actions were exercised from a real Pixel launcher widget. The widget survived an app upgrade and routed back to a single editor instance. |
| Static shortcuts | Release and debug resources target their own application IDs for compose, search, voice, camera and media. |
| Sharing | Android inbound text and mixed-media sharing reaches the editor. Memo-card outbound sharing currently exports text, not embedded attachment bytes. |

## Verification evidence

- JVM suite: 52 tests passed, including 10 speech routing/result/error tests.
- Pixel instrumentation suite: 19 tests passed (13 UI component tests, three
  social-section tests, one application-context test, one speech UI test and
  one real configured-`RecognitionService` smoke test).
- Debug, instrumentation-test and minified Release APK builds passed.
- `lintDebug` completed with zero errors for the accepted tree.
- Production canaries, their reactions/comments and uploaded attachments were
  deleted with exact-match cleanup; the pre-test memo and attachment counts were
  restored.

Run the release gate from the repository root:

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest assembleRelease
git diff --check
```

## Declared boundaries

- The Pixel's configured `RecognitionService` opened the microphone and
  recognized the deterministic Mandarin phrase “默默语音验收今天阳光很好”. The
  external recognizer Activity was launched and cancelled from the real editor;
  synthesized audio could not be returned through that Activity because its
  audio focus stopped listening, so editor insertion remains deterministically
  covered by controller/unit tests rather than claimed as a live Activity result.
- OPPO/vivo hardware was not available for this release. Their standard
  `RecognitionService`-only shape is covered by routing, permission and lifecycle
  tests, but each OEM/ROM still controls the actual recognition provider and
  language models.
- Memos has no native friends, visitor-history or guestbook data model, so those
  QQ Space concepts are not synthesized by the client.
- Server-side AI transcription remains optional; speech input uses the Android
  recognizer by default.
- GitHub Actions signing material is supplied only through repository secrets.
  Keystores, passwords, personal access tokens and server credentials must never
  be committed.
