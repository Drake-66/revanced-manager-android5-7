# ReVanced Manager builds for Android 6.0-7.1

Custom builds of ReVanced Manager, for Android 6.0 - Android 7.1

Combining with my [ReVanced Patches for YouTube 17.34.36](https://github.com/kitadai31/revanced-patches-android6-7), you can build YouTube ReVanced easily with an A6-7 device alone.

See also: https://github.com/kitadai31/revanced-patches-android6-7


## Download
Go to [Releases](https://github.com/kitadai31/revanced-manager-android6-7/releases) page

※ All Android 6.0 specific issues are solved.  
`Overlay Buttons` patch is selectable now.

<details>

<summary>Old information</summary>

**⚠ Limitations on Android 6.0 ⚠**

**If you are using Android 6.0, read this**

- DO NOT select `Overlay Buttons` patch!
  - If this patch is selected, patching will be aborted with `(exit code = 1)` while compiling resources.
  - If you need the overlay buttons, use other build methods.
- After patching, the "Install" button does not working.
  - When you press it, it says "There was a problem parsing the package."
  - Instead, export the APK and Install it later.

<img src="https://github.com/kitadai31/revanced-manager-android6-7/assets/90122968/ca98a8f5-a617-442f-9460-65009f114fad" width="240">

</details>

## Prerequisites
1. Android 6.0 or higher
2. Does not work on some armv7 (32bit) devices.  
If your device is armv7, you may get `(exit code = 135)` error.
3. Vanced MicroG (https://github.com/inotia00/VancedMicroG/releases) is required for YouTube and YouTube Music (Only for non-root)

## What I did to support Android 6 & 7

<details>

<summary>Click to open</summary>

- Change minSdkVersion to 23
- [Fix dependent library's problem](https://github.com/kitadai31/flutter_plugin_device_apps/commit/a8bff360982d7acb545b97c19c221560bc5ffa91)
- Change apksig library to [MuntashirAkon/apksig-android](https://github.com/MuntashirAkon/apksig-android). thank you!
- Enable java.nio coreLibraryDesugaring
- Remove usage of unsupported java.nio.file API from patches ([Patches side change](https://github.com/kitadai31/revanced-patches-android6-7/commit/aada74d77793c9783a7015a051474a1f6567eb60))
- Downgrade revanced-patcher's kotlin dependencies version (AGP 7.4.2 cannot desugar kotlin-stdlib 1.9 jar)

</details>

# How to patch
The usage is same as the original ReVanced Manager. This section describes the points specific to Android 6-7.

## YouTube
Official patches and latest Extended patches are not compatible with YouTube 17.34.36.  
(17.34.36 is the final version for Android 6 & 7.)

Therefore, change the patch source settings to [ReVanced Patches for YouTube 17.34.36](https://github.com/kitadai31/revanced-patches-android6-7), provided by me.

<img src="https://user-images.githubusercontent.com/90122968/230283820-dd55a454-6267-43dc-a6c0-eb1b6f5f4e15.png" width="240">

Read [How to build](https://github.com/kitadai31/revanced-patches-android6-7/wiki/How-to-build) page for details.

## YouTube Music
**ReVanced Music** - No problem with default patches.

**ReVanced Extended Music** - Unavailable. Inotia's music patch causes `NoClassDefFoundError`.

> **Note**  
> Instead of this ReVanced Manager, you can also use [Revancify](https://github.com/decipher3114/Revancify) or [RVX Builder](https://github.com/inotia00/rvx-builder) on A6/7 device.  
These solutions can build RVX Music.

## Other apps
You can patch as usual as original Manager.

Please note that some errors may occur, such as "NoSuchMethodError", "NoClassDefFoundError".  
If an error occurred and the error was specific to A6/7, please give up.

Known info: `dynamic-color` patch for Twitter causes error. Do not select it.

> **Warning**
> Sometimes, ReVanced Patches introduces breaking changes. If a breaking change is introduced, you will not be able to patch with the official patches until I update the Manager.

> **Note**  
> However, you can use [Revancify](https://github.com/decipher3114/Revancify) or [ReVanced Builder](https://github.com/reisxd/rvx-builder) instead of this ReVanced Manager.  
These solutions are stable and don't cause errors which caused by older Android.
>
> Also, if you have other Android 8+ devices (or PC), you can use the official ReVanced Manager on that device and transfer the patched APK to A6/7 devices.