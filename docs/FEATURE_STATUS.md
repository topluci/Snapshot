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
| Live depth | Native post effect samples `MainDepthSampler`; aperture/focus uniforms update live | `live_optics.fsh`, renderer smoke matrix |
| Depth fallback | 48xN or 64xN block-raycast depth grid for saved-image blur | `SceneDepthMap`, capture smoke cases |
| Film and effects | Four original color profiles, five simulated filters, moods, lens and sensor effects | `PhotoPipelineTest`, renderer smoke cases |
| Capture techniques | Three-frame bracket/HDR, three-focus merge, three-panel panorama | `suite` smoke case and generated files |
| Long exposure | Timed frame accumulation, two-press Bulb, tripod inventory stabilization | `PhotoPipelineTest`, Astro smoke cases |
| Astro 2.0 | Denoise, Deep Sky, Star Trails, interval programs, modeled dark correction, tracking assistance, Moon setup | `astro2` and Astro renderer matrix |
| Local exports | PNG, root screenshot copy, JSON metadata, optional pre-CPU-processing `.source.png` | Capture evidence validator and `clock` metadata case |
| In-game photographs | Photograph plus locked map-resolution Photo Map from a thumbnail | `controls` and multiplayer delivery cases |
| Image2Map | Automatic singleplayer command through loopback URL; helper sidecar for remote workflow | `image2map` smoke case requires a new 2x2 map bundle |
| Review tools | Lighttable, albums, favorites, ratings, comparison, deletion, metadata, journal | `ui` and `controls` smoke cases |
| Environment | Viewfinder-local time/weather preview; real changes gated by permission | Environment unit test and multiplayer allowed/denied cases |
| Renderer support | Vanilla, Sodium, Iris without pack, Iris with pack; labels in metadata | `scripts/test-renderers.sh` and `scripts/test-astro-renderers.sh` |

## Explicit non-features and limits

- The source export is a PNG before Snapshot's CPU capture processing, not a sensor RAW file.
- Lens and filter selections are simulations, not individual craftable equipment.
- The Camera Tripod stabilizes while carried; it is not placeable in the world.
- Live depth depends on the main depth texture. Transparent objects, particles, and shader effects may
  not contribute usable depth.
- The CPU depth fallback ray casts blocks, not every entity or particle.
- The Photograph viewer displays the embedded map image, not the local full-resolution PNG.
- Automatic Image2Map handoff is singleplayer-only.
- Environment presets control vanilla time and weather states; they do not directly edit shader-pack
  fog, cloud, wind, or sun-angle parameters.
- There is no video capture, scientific constellation catalogue, physical star tracker, villager
  profession, exhibition, multiplayer competition, wildlife AI, true panorama supersampling, placeable
  multi-size photo block, or server-hosted high-resolution gallery in this release.
- Compatibility is declared only for Minecraft 26.2. A successful build is not evidence for later
  Minecraft versions.
