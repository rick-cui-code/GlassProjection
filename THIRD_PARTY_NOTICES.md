# Third-party notices and provenance

## TabFold

This project grew out of a local adaptation of [6ZLeo/TabFold](https://github.com/6ZLeo/TabFold).
The current projection module is published separately from the original application.
The upstream MIT notices for 6ZLeo, TabFold contributors and Elijah Semyonov are retained in [LICENSE](LICENSE).

## Shizuku API and provider

`dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`:
[RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API), MIT License, copyright 2021 RikkaW.
See [licenses/Shizuku-API-LICENSE](licenses/Shizuku-API-LICENSE).
Shizuku Manager is installed separately and is not redistributed in this repository or APK.
The legacy API integration remains for compatibility; v0.4.11 no longer exposes a Shizuku setup button.

## Local wireless ADB dependencies

- `com.github.MuntashirAkon:libadb-android:3.1.1`: [source](https://github.com/MuntashirAkon/libadb-android/tree/3.1.1), Apache-2.0 license option, with BSD/MIT components.
- `org.conscrypt:conscrypt-android:2.5.3`: [source](https://github.com/google/conscrypt/tree/2.5.3), Apache-2.0 and bundled notices.
- Bouncy Castle 1.81 (`bcpkix`, `bcprov`, `bcutil`): [source](https://github.com/bcgit/bc-java/tree/r1rv81), Bouncy Castle license.
- `spake2-android:2.2.1`: [source and build instructions](https://github.com/MuntashirAkon/spake2-java/tree/2.2.1), LGPL-3.0. This unmodified Gradle dependency can be replaced and relinked by rebuilding this project. Reverse engineering for debugging modifications to this library is permitted under its license.
- AndroidX annotations 1.9.1: [AndroidX source](https://android.googlesource.com/platform/frameworks/support/), Apache-2.0.

Copyright notices and license texts are bundled in [assets/notices](projection-lab/src/main/assets/notices/README.txt), also accessible from the app. Dependencies retain their own licenses; the project's MIT license does not replace them. No signing keys are distributed.

## Gradle Wrapper

The Gradle 8.7 Wrapper files are distributed under the Apache License 2.0.
See [licenses/Gradle-LICENSE](licenses/Gradle-LICENSE) and [Gradle](https://github.com/gradle/gradle).

## Rendering references

[Ocisly14/iphone_duo](https://github.com/Ocisly14/iphone_duo), reviewed at commit
`1849d8ea3c3923de1fcf639c9092e300b83df6be`, informed the reference-plane projection and distance-blur design.
The Android implementation was written independently. No upstream Blender models, Python scripts,
wallpapers, video or other visual assets are included, and this repository does not grant a license to them.

[ajaxjiang96/FoldDepth](https://github.com/ajaxjiang96/FoldDepth) was an earlier visual reference.
Its application is not bundled.

## Diagnostic artwork

The numbered diagnostic artwork was generated for this project by `tools/bake_demo.py`.
It does not contain user screenshots or upstream wallpapers. Fonts are not distributed.
Optional regeneration uses NumPy and Pillow from the developer's environment.

## HiddenApiBypass

`org.lsposed.hiddenapibypass:hiddenapibypass:6.1`:
[LSPosed/AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass),
Apache License 2.0, copyright 2021-2025 LSPosed.
Used for compatibility with our own accessibility connection and callback state.
See [licenses/HiddenApiBypass-LICENSE](licenses/HiddenApiBypass-LICENSE).
