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
