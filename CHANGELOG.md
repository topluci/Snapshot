# Changelog

All notable user-facing changes to Snapshot are documented here.

## 0.1.0+26.2 - 2026-07-23

### Camera and capture

- Added five persistent camera preset slots for Landscape, Portrait, Wildlife, Astro, and Macro
  setups. Presets save the complete exposure, focus, lens, filter, aspect, and film state.
- Added Tiled 2X, a four-view high-resolution capture mode with rectilinear reprojection and exposure
  matching for stationary scenes.
- Improved three-panel panoramas with exposure matching.
- Added pickable entities to saved-image depth sampling.

### Live viewfinder

- Reworked live depth of field into a dynamic half-resolution bokeh pass with a full-resolution
  composite. Screenshot Ultra can retain a full-resolution bokeh pass.
- Added adaptive bokeh sampling and smoothed lens response to protect frame rate while changing
  aperture or focus.
- Added native gamepad viewfinder controls.
- Added configurable focus-peaking colour and strength, false-colour palette, HUD opacity, and HUD
  scale.

### Library and reliability

- Added a recoverable Trash view with restore, permanent-delete, and confirmed empty-trash actions.
- Made PNG capture and metadata writes validated and atomic, with an available-space guard before
  finalising large captures.
- Improved Photograph data handling and local texture loading.
- Added in-game calibration and high-resolution smoke scenarios alongside expanded unit and resource
  integrity tests.
- Added pull-request builds, tag-based GitHub releases, release packaging, and SHA-256 generation.

### Compatibility notes

- This release targets Minecraft 26.2, Fabric Loader 0.19.2 or newer, Fabric API 0.152.2+26.2 or
  newer, and Java 25 or newer.
- OpenGL is release tested with Vanilla, Sodium, and Iris. Minecraft 26.2's built-in experimental
  Vulkan backend is also release tested for viewfinder controls and capture on macOS through
  MoltenVK. The third-party VulkanMod renderer is not supported.
- Tiled 2X is sequential and is intended for stationary scenes. Transparent particles and some
  shader-specific effects may not provide usable depth.
