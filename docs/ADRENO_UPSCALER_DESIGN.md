# Adreno Temporal Reconstruction (ATR)

## Goal
Build an original temporal upscaler for Android GPUs such as the Adreno 730.
The design borrows general rendering concepts used by modern temporal
reconstruction systems; it is not NVIDIA DLSS and does not contain NVIDIA
proprietary code, models, or binaries.

## What the repository audit found
- The current Android app is primarily a diagnostic/settings UI.
- `ImageEffects.kt` contains a fragment shader, but no live game-frame source,
  EGL/Vulkan render loop, or GPU presentation path is connected to it.
- The APK inspector can identify libraries and graphics API markers, but static
  APK strings do not prove which renderer is active at runtime.
- Therefore the existing sliders save values; they do not currently alter a
  running game's frames.

## First GPU stage
`app/src/main/assets/shaders/adreno_temporal_upscale.comp` is an original
Vulkan compute shader prototype. It consumes:
1. Current low-resolution color.
2. Per-pixel motion vectors (UV units, current-to-previous convention).
3. Linear depth.
4. Previous reconstructed frame.
5. Output storage image.

It performs spatial reconstruction, motion-based history reprojection,
depth-aware history weighting, neighborhood clamping to reduce ghosting, and
conservative detail recovery.

## Required host integration (not yet implemented)
A Vulkan host must:
- Create descriptor set layout bindings 0-4 exactly as declared in the shader.
- Create a compute pipeline from the compiled SPIR-V.
- Supply valid motion vectors and linear depth from the game's renderer.
- Maintain ping-pong history images and invalidate history on camera cuts,
  resolution changes, loading screens, and invalid motion/depth.
- Dispatch in 8x8 groups, insert image layout/access barriers, and present the
  resulting image through the game's swapchain.
- Compile the shader to SPIR-V with a Vulkan GLSL compiler and validate it on
  the target Adreno driver.

## Critical Android limitation
A normal Android app cannot silently intercept and replace another app's
Vulkan/OpenGL frames. Applying this to a game requires one of:
- integration into that game's renderer/source,
- a supported game/plugin interface,
- or a rooted/system-level graphics interception layer (device- and game-specific).

MediaProjection/screen capture can process a captured display stream, but it is
not equivalent to injecting an upscaler into the game's render pipeline and
usually adds latency. This repository does not yet implement any of those
integration routes.

## Honest milestone
This commit adds the first GPU algorithm prototype and its integration contract.
It does not claim live in-game DLSS-like upscaling yet. The next milestone is a
native Vulkan Android module that compiles/dispatches this shader on Adreno 730,
then a separately tested frame-source integration path.
