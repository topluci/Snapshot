# Snapshot

Snapshot is a heavy-realism photography mod for Minecraft Fabric. It combines a
craftable survival camera, a DSLR-style viewfinder, local PNG capture, map-backed photographs, long
exposures, astrophotography tools, and an in-game light table.

Snapshot `0.1.0+26.2` is a beta built and tested specifically for Minecraft `26.2`.

## Requirements

- Minecraft `26.2`
- Fabric Loader `0.19.2` or newer
- Fabric API `0.152.2+26.2` or newer for Minecraft 26.2
- Java 25 or newer
  
Install Snapshot on the server for the complete multiplayer experience: survival camera checks,
paper consumption, Photograph and Photo Map delivery, commands, and authoritative time or weather
changes. Mod Menu, Sodium, Iris, and Image2Map are optional and are not bundled.

## Camera system

- Manual, Aperture Priority, Shutter Priority, Program, and Auto exposure modes
- Matrix, center-weighted, and spot metering with interpolated exposure adaptation
- ISO 50-12800, shutter speeds from 1/8000s to 30s plus Bulb, f/1.2-f/22, EV compensation, and
  2500-10000K white balance
- Auto ISO ceiling and minimum-shutter controls, three-frame bracketing, and HDR merge
- Half-press AF/AE lock, latched AE-L and AF-L, manual focus, and nine clickable AF points
- Smooth 18-200mm focal-length adjustment and six selectable lens simulations
- Simulated ND, graduated ND, polariser, diffusion, and infrared filters
- RGB histogram, highlight zebras, waveform, false colour, clipping readouts, and focus peaking
- Neutral, Warm 400, Muted Chrome, and Monochrome colour profiles
- Full, 4:3, square, and 2.39:1 framing, plus camera roll from -30 to +30 degrees
- Low, Medium, Ultra, and Screenshot Ultra optics presets

The lens and filter choices are camera profiles, not separate craftable lens or filter items.

## Optics and renderer behaviour

Snapshot reads Minecraft's main GPU depth texture for live foreground and background depth of field
when its native post-process chain is available. Aperture and focus changes are visible before the
shutter is released, with polygon aperture shapes, cat-eye bokeh, foreground spill, focus breathing,
film colour, rain droplets, condensation, and lens dust.

If another Minecraft post effect owns that chain, Snapshot preserves it and uses a capture-time
fallback based on block ray casts. Opaque scene geometry represented in the main depth texture can
participate in live blur. Transparent surfaces, particles, and some shader-specific effects may not
write usable depth and are therefore not guaranteed to blur correctly.

The final scene is captured from Minecraft's main render target while Snapshot hides the vanilla HUD,
crosshair, hotbar, held item, and hand. Captured-image processing can add physical exposure response,
motion blur, lens distortion, bloom, vignette, chromatic aberration, highlight halation, grain, and hot
pixels. Focus peaking is a composition assist and is suppressed during capture.

Snapshot detects Sodium and Iris at runtime and records the renderer and shader-pack state in metadata.
The release test matrix covers Vanilla, Sodium, Iris without a shader pack, and Iris with a shader pack.
Individual shader packs can still change depth, exposure, or post-processing behaviour.

## Long exposure and astrophotography

- Elapsed-time accumulation for 1s, 2s, 4s, 8s, 15s, 30s, and Bulb exposures
- A second shutter press ends Bulb, with a configurable maximum duration
- Camera settings and sampled depth freeze when the exposure begins
- Carrying `snapshot:tripod` locks camera orientation during long exposures
- `F12` applies a 24mm, f/1.8, ISO 1600, 15s, 3800K, infinity-focus night-sky setup
- Denoise, Deep Sky, and Star Trails temporal stacking modes
- Modelled dark-signal correction that scales with ISO and exposure time
- Sidereal and lunar tracking assistance, a 200mm Moon setup, red night-vision HUD, and composition guide
- 3x2s, 5x5s, and 10x10s interval programs with visible progress
- Exposure duration, stack mode, interval, frame count, stabilization, and shader state in metadata

These are renderer-based photographic simulations. Snapshot does not perform scientific sensor RAW
capture or identify real constellations from astronomical catalogues.

## Photographs and exports

With the default config, every completed image is written to `screenshots/snapshot/` and copied into
the root `screenshots/` folder. A matching `.snapshot.json` stores camera, location, renderer,
composition, and exposure metadata. The optional `.source.png` is the frame before Snapshot's CPU
capture processing. It is still a PNG and may already contain active renderer, shader-pack, or live
optics output; it is not a camera RAW file.

The DRIVE controls provide three-frame exposure brackets, HDR merge, three-distance focus stacking,
and a three-panel feathered panorama. The PRINT controls provide 128x128, 256x128, 256x256, and
384x256 map-art sizes.

On a Snapshot server, capture sends the title, dimensions, camera metadata, local-export status, and a
small RGBA thumbnail to create in-game items. It does not send the full-resolution PNG or its local
file path. The server returns a `snapshot:photograph` and a locked, filled map generated from that
thumbnail. Both display the same map-resolution image and work in ordinary item frames; right-clicking
the Photograph opens an enlarged map viewer.

Snapshot has no analytics or telemetry and does not upload photos to an external service.

## Image2Map integration

If Image2Map is installed in singleplayer and `capture.image2map_auto=true`, Snapshot submits the
finished local PNG to Image2Map at the selected print dimensions. Multi-map outputs are returned by
Image2Map as map items or a bundle. Snapshot exposes the PNG through a random, short-lived URL bound
only to the local loopback interface and stops that bridge when the game closes.

Automatic Image2Map handoff is singleplayer-only. For a remote server, the optional `.image2map.txt`
sidecar contains helper commands after the player chooses a URL that the server can reach. Direct
handoff is tested with Image2Map `0.14.0+26.2`.

## Lighttable and journal

Press `M` to open the local camera roll. It supports albums, favourites, 0-5 ratings, side-by-side
comparison, deletion, metadata, and composition scores. Press `Tab` to filter albums, `A` to file a
hovered image, and `J` from the lighttable to open the journal.

The journal records photographed biomes, weather, broad subject categories, celestial conditions,
assignments, total captures, and best composition score. Snapshot does not currently add villagers,
exhibitions, server competitions, or a scientific species catalogue.

## Default controls

- `V`: open or close the viewfinder; right-clicking the Camera also opens it
- Hold right mouse: momentary AF and AE lock; release to unlock
- Left mouse or `C`: release the shutter; press again to end Bulb
- Mouse wheel: smooth focal-length adjustment; `Ctrl` + wheel: camera roll
- `B` / `N`: next / previous setting; `+` / `-`: adjust the selected setting
- `F6`: AF/MF, `;`: flash, `H`: burst, `Backspace`: reset
- `F9`: exposure assist, `F10`: exposure mode, `F12`: astrophotography setup
- `` ` ``: camera command dial, `X`: clickable AF-point selector
- `[` / `]`: latch AE-L / AF-L
- `Z`: colour profile, `Y`: aspect ratio, `F8`: colour mood
- `U`: compact/expanded HUD, `F7`: environment preset, `J`: tutorial, `M`: lighttable

All bindings appear under the `Snapshot` category and can be remapped.

## Survival, environment, and commands

Survival access requires `snapshot:camera` and consumes `snapshot:photographic_paper` by default.
Recipes are included for the Camera, Photographic Paper, and Camera Tripod. Creative players bypass
those requirements. Configure access, export, optics, stacking, local environment preview, and
permission levels in `config/snapshot.properties`.

While the viewfinder is open, the selected time or weather preset can be previewed locally when
`environment.client_preview=true`. Applying that preset to the real world requires singleplayer
ownership or the configured server permission, level 2 by default.

Commands:

- `/snapshot`: display active server config
- `/snapshot give camera`: give a Camera, permission level 2
- `/snapshot preset <low|medium|ultra|screenshot_ultra>`: change the invoking player's client preset
- `/snapshot env apply <clear|rain|storm|sunrise|noon|sunset|night>`: apply a real environment change,
  permission level 2
- `/snapshot reload`: reload the server config, permission level 2

## Testing

Run `./gradlew clean build` for compilation, JSON/resource validation, and unit tests. Run
`./scripts/test-release.sh` for the local singleplayer release suite and renderer matrices. The suite
checks capture/export, HDR, focus stacking, panorama, Astro 2.0, direct Image2Map output, lighttable,
journal, controls, survival access, item artwork, and Low/Medium/Ultra performance scenarios.

```shell
./gradlew snapshotSmokeTest -PsnapshotSmokeCase=suite -PsnapshotSmokeWorld="New World"
```

Available cases are `suite`, `clock`, `image2map`, `ui`, `astro2`, `items`, `access`, `controls`,
`performance`, `renderer`, `multiplayer_denied`, and `multiplayer_allowed`.
`./scripts/test-renderers.sh` covers Vanilla, Sodium, Iris without shaders, and Iris with shaders.
`./scripts/test-astro-renderers.sh` repeats that matrix for Astro 2.0. Machine-readable reports are
written to `run/snapshot-test-results/`; captures and QA screenshots remain in `run/screenshots/` for
manual visual review.

Automated tests can verify state, generated files, renderer labels, inventory delivery, and timing.
They do not replace visual inspection across every shader pack, graphics driver, resolution, or server.

## License

Snapshot's original code and assets are Copyright (c) 2026 luci and released as All Rights
Reserved/No License. Official unmodified releases may be used for personal gameplay; redistribution,
modpack inclusion, modified releases, and reuse of Snapshot code or assets require prior written
permission from luci. See `LICENSE`.

The generated viewfinder glyph atlas is derived from Noto Sans JP and remains under the SIL Open Font
License 1.1. Third-party materials retain their own terms; see `NOTICE.md` and `FONT_LICENSE.md`.
