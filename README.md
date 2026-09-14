# Spatial Chess (PICO Spatial SDK)

An offline, Shared Space chessboard for PICO OS 6. The board lives in one volumetric window that you place near a table with the system window handles; move either side freely, capture by storing the taken piece first, undo/redo, promote pawns, autosave locally, and switch between English and 中文 in place (English on first launch). Shared Space has no plane detection yet, so there is no table anchoring. Built from `Spatial_Chess_PRD_UIUX.docx` and the Figma file "Spatial Chess 自由摆棋 PRD 与 UI UX".

Demo (PICO Emulator, 43 s): [`deliverables/demo.mp4`](deliverables/demo.mp4)

## Highlights

- **UI 1.1 (Figma page "04 实装改版")**: title and save state merged into one centred card; the icon toolbar (Figma icon exports, tinted at runtime, hover labels and content descriptions) sits above the board; a translucent, dismissible tip with an "!" badge is shown at start and hidden with one tap until a different message arrives; up/down arrows on the right tilt the board ±20° per tap (0–40°); Settings is a card above the board with three columns (Display · Board · Language) that collapses into tabs when the board is tilted to 40° or scaled to 130 %+.
- **Container**: one `Form.Volumetric` `DefaultWindowContainer` (1.3 × 0.62 × 1.0 m, `WorldScale.Fixed`, `VolumeAlignment.Gravity`); the Stage is never opened.
- **UI**: PICO Spatial UI — `PicoTheme` (Figma colours + **PICO Sans** from `/system/fonts/PICOSans.ttf`, default font as fallback), `Augment` (top stack and side tilt control), `IconButton`, in-volume `AttachmentPanel` (context panel, tray counts, coordinates), `Sheet` (help), `AlertDialog` (reset).
- **3D**: `SpatialView` + ECS. Hierarchy `sceneRoot → tiltRoot (pitch about the window X axis, pivot on the near board edge) → boardRoot (yaw · scale · height)`; board, pieces and trays tilt together while the UI stays upright. The wooden rim (`rim:front/back/left/right` colliders) can be grabbed: dragging along the rim turns the board 90° every 12 cm in the direction the edge is pushed, lifting or lowering it tilts ±20° every 6 cm — the same steps as Settings and the side arrows, saved immediately. Grab deltas go through the full inverse chain (tilt⁻¹ · yaw⁻¹ · scale⁻¹) into board space and snap along the board normal. `chess.usdz` is loaded once and its 32 prims re-parented to per-piece pivots (Z-up detected and corrected); 64 square colliders, two trays and the selection / target / capture / blocked markers come from `assets/primitives.usdz` (the SDK's `MeshResource.createBox` crashes on some PICO OS devices that lack the foundation extension classes). Promotion clones hidden prototypes.
- **Input**: `detectSpatialTapGesture` (select-then-target) and `detectSpatialDragGesture` (grab; the grab offset is kept, release projects onto the nearest valid square and snaps in 120 ms).
- **Data**: `BoardState` is the single source of truth; Move / Capture / Store / Restore / Promote / Reset / Clear each produce a full snapshot and one history entry (≤ 120). Saves go temp file → validation → atomic rename, with one backup kept.
- **Localization**: `assets/locales/{zh-CN,en-US}.json` with identical keys and placeholders; a missing key falls back to en-US. First launch is English; a manual choice is persisted.
- **Sound**: `assets/sfx/*.ogg`, played at the piece position through the Spatial SDK, `SoundPool` as fallback.

## Downloads

- **APK**: see GitHub Releases (`SpatialChess-v<version>.apk`; install on PICO Emulator 6.1 or a PICO OS 6 device with `pico-cli app install <apk>`).
- **Emulator verification report**: [`deliverables/SpatialChess_Emulator_Verification.xlsx`](deliverables/SpatialChess_Emulator_Verification.xlsx) (49 captured states with the PRD coverage table); individual screenshots in [`deliverables/screenshots/`](deliverables/screenshots/).

## Build and run

```bash
export JAVA_HOME=~/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home
./gradlew :app:assembleDebug
pico-cli emulator start            # or a connected PICO device
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.spatialchess
pico-cli capture screenshot --out shot.png
```

## Debug channel (the emulator cannot script spatial input)

```bash
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd select --es arg e2
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd square --es arg e4
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd drag --es arg "'g2 g4'"
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd view --es arg "30,20,1.6"   # orbit camera yaw,pitch,zoom
adb shell am broadcast -a com.example.spatialchess.DEBUG --es cmd state
```

Other commands: `tap` `store` `cancel` `confirm` `undo` `redo` `reset` `resetStandard` `clear` `settings` `apply` `help` `closeHelp` `promote <queen|rook|bishop|knight>` `skipPromotion` `lang <zh|en>` `orient <deg>` `tilt <0|20|40>` `tiltUp` `tiltDown` `dismissHint` `rim <front|back|left|right>:<dx>,<dy>,<dz>` `scale <80-140>` `height <cm>` `coords <on|off>` `reduceMotion <on|off>` `failsave <on|off>` `save` `pieces` `wipe`. They run through the same ViewModel paths as real input and exist only for testing.

## Layout

- `app/src/main/java/com/example/spatialchess/`
  - `Main.kt` entry DSL · `ui/` theme, icons and Spatial UI · `scene/ChessScene.kt` 3D scene · `app/ChessViewModel.kt` state machine · `model/Chess.kt` data and commands · `data/` persistence · `i18n/` copy · `debug/` debug channel
- `deliverables/` emulator verification workbook, screenshots, demo video, release notes
