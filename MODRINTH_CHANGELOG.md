Snapshot 0.1.0 is the first release, bringing detailed capture controls, live optics, and safe local
photo management to Minecraft 26.2.

- Added five persistent full-camera preset slots.
- Added exposure-matched Tiled 2X capture for larger still-scene images and improved panoramas.
- Reworked live depth of field with a dynamic half-resolution bokeh pass, full-resolution composite,
  adaptive sampling, and smoother focus/aperture response.
- Added native gamepad viewfinder controls and configurable peaking, false-colour, and HUD settings.
- Added recoverable Trash with restore and confirmed permanent deletion.
- Made PNG and metadata output validated and atomic, with an available-space guard.
- Expanded entity-aware depth sampling, calibration/high-resolution smoke checks, unit tests, and
  release automation.

Requires Fabric Loader 0.19.2+, Fabric API 0.152.2+26.2+, and Java 25. OpenGL is tested with Vanilla,
Sodium, and Iris. Minecraft 26.2's built-in experimental Vulkan backend is also tested on macOS
through MoltenVK. Third-party VulkanMod is not supported. Tiled 2X is intended for stationary scenes.
