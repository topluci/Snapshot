# Snapshot

Snapshot turns Minecraft into a hands-on photography sandbox. Compose through a compact DSLR-style
viewfinder, control exposure and focus, save local PNGs, and carry the result as a Photograph or
locked Photo Map.

Snapshot `0.1.0+26.2` targets Minecraft `26.2`.

## Requirements

- Fabric Loader `0.19.2` or newer
- Fabric API `0.152.2+26.2` or newer for Minecraft 26.2
- Java 25 or newer

Install Snapshot on the server for survival item requirements, paper consumption, Photograph and Photo Map delivery, commands, and permission-controlled world changes. Image2Map is optional and not included in the download.

With cheats or operator permission, `V` opens the viewfinder without an item. Otherwise, hold and
right-click the Camera. Opening an inventory, chest, pause menu, or other non-camera screen closes the
viewfinder and cancels pending captures so interface clicks can never release the shutter.

## Camera controls

- M, A/Av, S/Tv, P, and Auto exposure modes
- ISO 50-12800, 1/8000s-30s and Bulb shutter, f/1.2-f/22, EV compensation, and 2500-10000K WB
- Smooth 18-200mm focal length, manual focus, half-press AF/AE lock, AE-L, AF-L, and nine AF points
- Matrix, centre-weighted, and spot metering with Auto ISO limits
- Histogram, zebras, waveform, false colour, clipping warnings, and focus peaking
- Exposure bracketing and HDR, focus stacking, exposure-matched panorama, Tiled 2X, burst, flash,
  and aspect-ratio masks
- Six lens simulations, five filter simulations, four colour profiles, and four quality presets
- Five persistent user preset slots: Landscape, Portrait, Wildlife, Astro, and Macro

## Live optics and shaders

When Snapshot can use Minecraft’s post-process chain, it reads the main GPU depth texture for live foreground and background depth of field. Aperture, focus distance, aperture-blade shape, cat-eye bokeh, focus breathing, colour grading, and weather-on-lens effects update in the viewfinder.

Snapshot preserves an existing Minecraft post effect and falls back to block-depth processing for
the saved capture. Transparent surfaces, particles, and shader-specific effects are not guaranteed to
write usable depth. Snapshot detects Sodium and Iris, preserves the rendered shader scene in captures,
and records renderer and shader-pack state in metadata. Shader packs can still alter depth and
post-processing behaviour.

Live depth-of-field preserves the sharp scene at full resolution, renders depth-aware bokeh into a
half-resolution transient target, then composites and grades at full resolution. Screenshot Ultra or
`optics.half_resolution_dof=false` keeps the bokeh pass full resolution. Snapshot also reduces its
sample count when measured frame time falls below the configured target and restores detail when
performance recovers. The target FPS, depth response stability, focus-peaking colour and strength,
false-colour palette, HUD opacity, and HUD scale are editable in `config/snapshot.properties` or Mod
Menu.

Native gamepad viewfinder controls are enabled by default. Start toggles the finder, the triggers
handle half-press and shutter, the shoulder buttons cycle controls, the D-pad changes values, the
right stick zooms, and the face buttons provide AF, AF-point, command-dial, and close actions.

OpenGL is release tested with Vanilla, Sodium, and Iris. Minecraft 26.2's built-in experimental
Vulkan backend is also release tested for viewfinder controls and capture on macOS through MoltenVK.
The separate third-party VulkanMod renderer is not a supported configuration.

## Long exposure & Astro

- Timed accumulation at 1s, 2s, 4s, 8s, 15s, and 30s, plus two-press Bulb
- Denoise, Deep Sky, and Star Trails temporal stacking
- Modelled dark-signal correction, interval programs, and sidereal/lunar tracking assistance
- 24mm night-sky and 200mm Moon setups, red night vision, and a composition guide
- A Camera Tripod item that stabilises long exposures while it is carried

These are artistic renderer-based simulations, not scientific sensor RAW capture or an astronomical
catalogue.

## Files & Privacy

Default captures save to `screenshots/snapshot/` and the root `screenshots/` folder. Snapshot writes
to a temporary file, validates the decoded PNG, checks available disk space, and atomically finalises
the capture. Metadata and Image2Map sidecars are also atomically replaced. Snapshot can additionally
write a pre-CPU-processing source PNG; it may already include renderer, shader-pack, or live-optics
output.

On a connected Snapshot server, the client sends camera metadata and a small RGBA thumbnail to create the Photograph and locked Photo Map. It does not send the full-resolution PNG or its local path. Snapshot has no analytics or telemetry and does not upload photos to an external service.

The local lighttable provides albums, favourites, ratings, comparison, metadata, and a photography
journal. Deleted captures move to a recoverable Trash view with Restore, Delete Forever, and confirmed
Empty Trash actions. Photograph items open an enlarged map-resolution viewer and display in ordinary
item frames.

## Verification

`./gradlew test` runs camera, processing, storage, metadata, and resource-integrity tests. The
`calibration` smoke case constructs a temporary optical chart in the game, captures aperture, focus,
ISO, shutter, and left/right AF-point variants, records numerical image measurements, then removes the
fixture. The `highres` case renders four views, verifies the stitched PNG is substantially larger than
the framebuffer, and checks its four-frame metadata. Release and Modrinth steps are in `PUBLISHING.md`.

## Image2Map

With Image2Map installed in singleplayer, Snapshot can automatically create the selected multi-map
print. It temporarily serves the local PNG through a random URL bound only to `127.0.0.1`, then stops the bridge when Minecraft closes. Automatic handoff does not operate on remote servers; an optional helper sidecar is provided for that workflow. Tested integration version: Image2Map `0.14.0+26.2`.

## Important limits

- This release targets Minecraft `26.2` only.
- Lenses and filters are selectable simulations, not separate equipment items.
- The tripod stabilises from inventory; it is not a placeable tripod entity.
- The in-game Photograph uses map resolution. Full-resolution review is available from the local
  lighttable PNGs.
- Tiled 2X captures four overlapping views, rectilinearly reprojects and exposure-matches them, then
  saves a canvas around 1.8 times the framebuffer width and height. It is best for stationary scenes.
- Same-frame off-screen supersampling, a translucent/particle depth pre-pass, depth-history
  reprojection, and 16-bit output are not implemented yet.
- Snapshot currently has no villagers, exhibitions, multiplayer competitions, video capture, true
  camera RAW, scientific constellation identification, or placeable multi-size photo blocks.

Snapshot’s original code and assets are Copyright (c) 2026 luci and released as All Rights
Reserved/No License. Redistribution, modpack inclusion, modified releases, and reuse require luci's prior written permission.

Its generated viewfinder glyph atlas is derived from Noto Sans JP and
remains under the SIL Open Font License 1.1.
