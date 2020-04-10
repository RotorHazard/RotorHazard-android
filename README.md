![Android CI](https://github.com/RotorHazard/RotorHazard-android/workflows/Android%20CI%20master/badge.svg)
# RotorHazard-Android

A band scanner Android app which uses RotorHazard node hardware connected to the device.

Latest APK can be found in the
[master CI workflow](https://github.com/RotorHazard/RotorHazard-android/actions?query=workflow%3A%22Android+CI+master%22)
else go to the [releases page](https://github.com/RotorHazard/RotorHazard-android/releases). You will need to allow installation from untrusted sources to load this app.

## Usage

Connect one node via USB with an OTG cable and launch app. A single node can be assembled as in the [RotorHazard USB node documentation](https://github.com/RotorHazard/RotorHazard/blob/master/doc/USB%20Nodes.md).

Drag to pan and pinch to zoom. The green line shows most recetly recorded value. The red line shows historical high and the blue line shows historical low since last app launch.

Nodes connected to a timer that provides [in-system programming](https://github.com/RotorHazard/RotorHazard/wiki/Specification:-In-System-Programming) capabilites may not function with this app because serial communications are disabled.
