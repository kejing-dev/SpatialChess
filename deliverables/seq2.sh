#!/bin/bash
S="$(cd "$(dirname "$0")" && pwd)"; D="$S/dbg.sh"; SH="$S/shot.sh"; OUT="$S/final"; mkdir -p "$OUT"
LOG="$OUT/states.txt"; : > "$LOG"
st() { adb -s emulator-5554 logcat -d -s SpatialChess.VM:* 2>/dev/null | grep "phase=" | tail -1 | sed 's/.*VM: //'; }
snap() { local name="$1"; sleep "${WAIT:-1.3}"; adb -s emulator-5554 exec-out screencap -p > "$OUT/$name.png"; "$D" state >/dev/null; echo "$name|$(st)" >> "$LOG"; echo "shot $name"; }
"$D" resetStandard; sleep 1.5; "$D" cancel
snap 01_zh_idle
"$D" select e2;                       snap 02_zh_selected
"$D" square e4;                       snap 03_zh_moved
"$D" select d7; "$D" square d5;       snap 04_zh_black_moved
"$D" select e4; "$D" tap d5;          snap 05_zh_capture_preview
"$D" confirm;    WAIT=1.8             snap 06_zh_captured
"$D" undo;                            snap 07_zh_undo
"$D" redo;                            snap 08_zh_redo
"$D" select d5; "$D" store;           snap 09_zh_stored
"$D" selectId w_pawn_e;               snap 10_zh_tray_selected
"$D" square e4;                       snap 11_zh_restored
"$D" settings;                        snap 12_zh_settings_sheet
"$D" apply; sleep 0.8
"$D" reset;                           snap 13_zh_reset_dialog
"$D" cancel; sleep 0.8
"$D" select e4; "$D" square e5; sleep 1.3; "$D" select e5; "$D" square e6; sleep 1.3
"$D" select e6; "$D" tap e7; "$D" confirm; sleep 1.8
"$D" select e7; "$D" tap e8; "$D" confirm; WAIT=1.8 snap 14_zh_promotion_panel
"$D" promote queen;                   snap 15_zh_promoted
"$D" orient 90;                       snap 16_zh_orient_90
"$D" orient 180;                      snap 17_zh_orient_180
"$D" orient 0; sleep 0.8
"$D" scale 140;                       snap 18_zh_scale_140
"$D" scale 80;                        snap 19_zh_scale_80
"$D" scale 100; sleep 0.8
"$D" coords on;                       snap 20_zh_coords_on
"$D" coords off; sleep 0.5
"$D" failsave on;                     snap 21_zh_save_failed
"$D" failsave off; "$D" save; sleep 0.5
"$D" help;                            snap 22_zh_help_sheet
"$D" closeHelp; sleep 0.8
"$D" anchor;                          snap 23_zh_placement_mode
"$D" confirm; sleep 0.8
"$D" drag g2 g4;                      snap 24_zh_drag_g2_g4
"$D" view "30,20,1.6";                snap 25_view_left_front
"$D" view "-30,20,1.6";               snap 26_view_right_front
"$D" view "0,60,1.6";                 snap 27_view_top_down
"$D" view "180,20,1.6";               snap 28_view_black_side
"$D" view "90,15,1.6";                snap 29_view_side
"$D" view "0,0,1"; sleep 0.8
"$D" lang en;                         snap 30_en_idle
"$D" select a2;                       snap 31_en_selected
"$D" cancel; "$D" settings;           snap 32_en_settings_sheet
"$D" apply; "$D" reset;               snap 33_en_reset_dialog
"$D" cancel; "$D" help;               snap 34_en_help_sheet
"$D" closeHelp; "$D" lang zh; sleep 0.5
"$D" pieces; sleep 0.5
adb -s emulator-5554 logcat -d -s SpatialChess.VM:* 2>/dev/null | grep -A33 "pieces:" | grep -E "(w_|b_)" > "$OUT/pieces.txt"
adb -s emulator-5554 logcat -d -s SpatialChess.Sfx:* 2>/dev/null | grep -c "sfx" > "$OUT/sfx_count.txt"
echo done
