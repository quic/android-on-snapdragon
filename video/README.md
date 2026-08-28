# QtiVideoExt

`QtiVideoExt` is an Android support library and set of sample applications for using Qualcomm video codec extensions on Snapdragon platforms. The library exposes Qualcomm `MediaCodec` vendor extension keys, helpers for querying supported parameter ranges, and utilities for muxing and extracting depth-video MP4 content.

## Modules

- `libraries/QtiVideoExt`: Android library that exposes codec extension constants, capability-query helpers, and depth MP4 utilities.
- `demos/app`: Minimal codec capability demo that logs supported vendor parameters for video codecs.
- `demos/depthcapture`: Depth capture and playback demo using `DepthMuxer`, `DepthExtractor`, and OpenGL rendering.
- `sample-apps/app`: Sample transcoding application for ROI, MBROI, LTR, QP control, QP override, slice/resync, ProSight, HDR10, HDR10+, and SDR workflows.

## Requirements

- Android Studio or a compatible local Gradle installation.
- Android SDK 33.
- Java 11.
- A Snapdragon device running Android 13 or later for exercising Qualcomm vendor codec extensions.
- Approved public test media for sample and instrumented-test workflows.

## Build

Open the repository root in Android Studio and build the desired module.

For command-line builds, use the Gradle wrapper if present in your checkout:

```bash
./gradlew assemble
```

If the Gradle wrapper files are not present, use a compatible locally installed Gradle version with Android Gradle Plugin 7.4.1:

```bash
gradle assemble
```

## Test

Run local checks with:

```bash
./gradlew check
```

Some instrumented tests require media files to be pushed to the app-specific external files directory on a device. Use only approved public test clips and avoid sharing proprietary, confidential, personal, or internal-only data in issues, pull requests, logs, or test artifacts.

## Usage Overview

Use `qti.video.QMediaExtensions` constants with Android `MediaFormat` and `MediaCodec.setParameters()` to configure supported Qualcomm video extensions.

Use `qti.video.QMediaCodecCapabilities.createForCodec(...)` to query supported vendor parameters and ranges for a codec and MIME type before enabling optional extensions.

Use `qti.video.depth.DepthMuxer` to create MP4 files containing depth tracks. Tracks with `DepthFormat.KEY_TRACK_TYPE` are written into the inner depth clip; tracks without that key are written into the outer clip.

Use `qti.video.depth.DepthExtractor` to read standard and depth tracks from a depth-enabled MP4 file through a single public track list.

## Development

Development happens on the `master` branch. Contributors should submit pull requests against `master`.

Before opening a pull request:

- Run relevant Gradle checks.
- Add or update tests where practical.
- Update documentation for public API or behavior changes.
- Include DCO signoff on all commits.
- Confirm no internal-only information is included.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution details.

## Security

Report sensitive or not-yet-public vulnerabilities through Qualcomm Product Security. See [SECURITY.md](SECURITY.md) for details.

## License

QtiVideoExt is licensed under the Apache-2.0 license. See [LICENSE.txt](LICENSE.txt) for the full license text.
