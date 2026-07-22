# Snapshot Feature Status

This file is the release claim ledger for Snapshot `0.1.0+26.2`. Public descriptions should not claim
more than the implemented column below.

| Area | Implemented in 0.1.0 | Evidence |
| --- | --- | --- |
| Identity | `snapshot`, Snapshot, author luci, `com.luci.snapshot` | Resource-integrity tests inspect `fabric.mod.json`, entrypoints, mixins, recipes, models, and branding |
| Survival camera | Camera requirement, paper consumption, creative bypass, recipes | `access` smoke case and multiplayer delivery cases |
| Exposure | M/A/S/P/Auto, ISO, shutter/Bulb, aperture, EV, WB, metering, Auto ISO limits | `CameraSettingsTest`, `controls` smoke case |
| Focus and lens | AF/MF, half-press, AE-L/AF-L, nine AF points, smooth 18-200mm, six lens simulations | `CameraSettingsTest`, `controls` screenshots and assertions |
| Exposure assists | RGB histogram, zebras, waveform, false color, clipping, focus peaking | HUD implementation and `controls` smoke case |
| Live depth | Full-resolution scene plus dynamic half-resolution depth bokeh and full-resolution composite; aperture/focus update live; adaptive taps and smoothed lens response protect frame rate | Split live-optics shaders, calibration, performance, and renderer smoke cases |
| Depth fallback | 48xN or 64xN block and pickable-entity raycast grid for saved-image blur | `SceneDepthMap`, capture smoke cases |
| Film and effects | Four original color profiles, five simulated filters, moods, lens and sensor effects | `PhotoPipelineTest`, renderer smoke cases |
| Capture techniques | Three-frame bracket/HDR, three-focus merge, exposure-matched three-panel panorama, and four-view Tiled 2X with rectilinear reprojection at about 1.8x width/height | `suite` and `highres` smoke cases plus decoded generated files and metadata |
| Long exposure | Timed frame accumulation, two-press Bulb, tripod inventory stabilization | `PhotoPipelineTest`, Astro smoke cases |
| Astro 2.0 | Denoise, Deep Sky, Star Trails, interval programs, modeled dark correction, tracking assistance, Moon setup | `astro2` and Astro renderer matrix |
| Local exports | Validated atomic PNG, disk-space guard, root copy, atomic JSON metadata, optional pre-CPU-processing `.source.png` | `SnapshotCaptureStorageTest`, capture evidence validator, and `clock` metadata case |
| In-game photographs | Photograph plus locked map-resolution Photo Map from a thumbnail | `controls` and multiplayer delivery cases |
| Image2Map | Automatic singleplayer command through loopback URL; helper sidecar for remote workflow | `image2map` smoke case requires a new 2x2 map bundle |
| Review tools | Lighttable, albums, favorites, ratings, comparison, recoverable Trash, restore/permanent deletion, metadata, journal | Trash-store tests plus `ui` and `controls` smoke cases |
| Camera presets | Five named slots persist the complete camera, lens, filter, aspect, and film state across sessions | `CameraPresetStoreTest` and command-dial preset page |
| Accessibility | Configurable peaking/false-colour palettes, peaking strength, HUD opacity/scale, remappable mouse controls, native gamepad viewfinder input | Config tests, registered mappings, and `SnapshotGamepadInput` |
| Calibration | Disposable optical scene and ten measured captures for aperture, focus, ISO, shutter, and selectable AF points | `calibration` smoke case and JSON metrics |
| Release automation | Pull-request CI, tag-based GitHub releases, checked JARs, SHA-256 file, local full-suite script, publication checklist | `.github/workflows`, `scripts/package-release.sh`, `PUBLISHING.md` |
| Environment | Viewfinder-local time/weather preview; real changes gated by permission | Environment unit test and multiplayer allowed/denied cases |
| Renderer support | OpenGL: Vanilla, Sodium, Iris without pack, Iris with pack; Minecraft 26.2 built-in experimental Vulkan: controls and capture on macOS through MoltenVK | OpenGL renderer matrices plus Vulkan `renderer` and `controls` smoke cases |

## Explicit non-features and limits

- The source export is a PNG before Snapshot's CPU capture processing, not a sensor RAW file.
- Lens and filter selections are simulations, not individual craftable equipment.
- The Camera Tripod stabilizes while carried; it is not placeable in the world.
- Live depth depends on the main depth texture. Transparent objects, particles, weather, and shader
  effects may not contribute usable depth.
- The CPU depth fallback ray casts blocks and pickable entities, not particles or every translucent
  renderer layer.
- The Photograph viewer displays the embedded map image, not the local full-resolution PNG.
- Automatic Image2Map handoff is singleplayer-only.
- Environment presets control vanilla time and weather states; they do not directly edit shader-pack
  fog, cloud, wind, or sun-angle parameters.
- Live DoF uses a dynamic half-resolution bokeh target by default, with a full-resolution composite.
  A dedicated translucent depth pre-pass and depth-history reprojection are not implemented.
- Tiled 2X is a sequential four-view capture intended for stationary scenes. It is not a same-frame
  off-screen render, and shader temporal effects or moving subjects can differ between tiles.
- There is no video capture, scientific constellation catalogue, physical star tracker, villager
  profession, exhibition, multiplayer competition, wildlife AI, off-screen supersampling, 16-bit
  output, placeable multi-size photo block, or server-hosted high-resolution gallery in this release.
- Compatibility is declared only for Minecraft 26.2. A successful build is not evidence for later
  Minecraft versions.
- OpenGL is release tested with Vanilla, Sodium, and Iris. Minecraft 26.2's built-in experimental
  Vulkan backend is release tested for viewfinder controls and capture on macOS through MoltenVK.
  The third-party VulkanMod renderer is not supported.
