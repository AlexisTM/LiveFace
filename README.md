# LiveFace

The goal is to fetch your face features to be used with UE5 metahumans and other applications.

This is heavily based out of the vision examples from MLKit with fixes to make it work on my earlier Android version (API 30, Android 11).

This includes:
- Face detection (biometry points such as lips, eyebrows, ...)
- Mesh detection (3D mesh points of your face)
- Pose detection (The human poses)


How to use:
https://www.youtube.com/watch?v=lDFLrzZy2R4

Build the UE5 plugin
===============

1. Create an empty C++ project
1. Copy the CborLiveLink plugin within the newly created project, within a `Plugins` folder
1. Remove the Binaries and Intermediate folders
1. Open the project with Unreal Engine 5
1. Once loaded: Tools > Open Visual Studio (or the .sln file directly)
1. Within Visual Studio, right click the module > Rebuild

Example: https://www.youtube.com/watch?v=1ogc1TdhGvE
