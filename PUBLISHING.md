# Snapshot Publishing Checklist

Snapshot is client-and-server Fabric software for Minecraft 26.2. It is Copyright (c) 2026 luci,
All Rights Reserved/No License.

## 1. Verify locally

1. Use Java 25 and run `./scripts/package-release.sh`.
2. Run `./gradlew snapshotSmokeTest -PsnapshotSmokeCase=controls --console=plain`.
3. Run `./gradlew snapshotSmokeTest -PsnapshotSmokeCase=calibration --console=plain`.
4. Run `./gradlew snapshotSmokeTest -PsnapshotSmokeCase=highres --console=plain`.
5. Run `./gradlew snapshotSmokeTest -PsnapshotSmokeCase=performance --console=plain`.
6. Run `./scripts/test-renderers.sh` and `./scripts/test-astro-renderers.sh` for the installed renderer matrix.
7. Review `run/snapshot-test-results/` and the QA PNGs in `run/screenshots/`.
8. Confirm `docs/FEATURE_STATUS.md` contains no claim beyond the tested implementation.

The uploadable JAR and `SHA256SUMS.txt` are written to `build/libs/`. The `-sources.jar` is not the
player download.

## 2. Publish GitHub

1. Commit the intended source, assets, documentation, and workflows.
2. Push `main` and wait for the Build workflow to pass.
3. The artifact version is `0.1.0+26.2`. Because that version's normal tag name is already occupied
   by the earlier beta, use a distinct immutable tag such as `git tag v0.1.0+26.2-release`.
4. Push the distinct tag with `git push origin v0.1.0+26.2-release`.
5. The Release workflow builds from that tag, validates the JAR, writes its SHA-256 checksum, and
   creates the GitHub release automatically.
6. Download the workflow artifact once and compare it with `SHA256SUMS.txt` before publishing the
   same binary elsewhere.

Tags are immutable release records. In particular, do not silently move or replace `v0.1.0+26.2`;
it points to the earlier beta even though the final first-release artifact is also versioned
`0.1.0+26.2`.

## 3. Publish Modrinth

Use the same checked release JAR. Do not upload the sources JAR.

- Project type: Mod
- Environment: Client and server
- Game version: 26.2
- Loader: Fabric
- Required dependency: Fabric API
- Optional integrations: Mod Menu, Sodium, Iris, Image2Map
- Java requirement: Java 25
- License: All Rights Reserved
- Version title: `Snapshot 0.1.0 for Minecraft 26.2`
- Changelog: use `MODRINTH_CHANGELOG.md`, keeping its claims aligned with `docs/FEATURE_STATUS.md`

After publication, install the downloaded Modrinth file into a clean instance and repeat the
`controls` capture path once. This catches upload mistakes, incorrect dependencies, and stale JARs.

## Release blockers

Do not publish when any unit test, resource-integrity test, smoke report, renderer check, checksum,
or clean-instance launch fails. Dedicated translucent/particle depth, same-frame off-screen
supersampling, depth-history reprojection, and 16-bit HDR export are not release claims until their
implementation and tests land.
