package com.example.spatialchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spatialchess.app.ChessViewModel
import com.example.spatialchess.app.Panel
import com.example.spatialchess.app.Phase
import com.example.spatialchess.app.SaveState
import com.example.spatialchess.data.Settings
import com.example.spatialchess.i18n.L10n
import com.example.spatialchess.model.Kind
import com.example.spatialchess.model.Location
import com.example.spatialchess.model.Side
import com.example.spatialchess.scene.ChessScene
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ButtonSize
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.design.windows.Toolbar
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.geometry.DpOffset3D
import com.pico.spatial.ui.foundation.geometry.NormalizedPoint3D
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.window.Augment
import com.pico.spatial.ui.foundation.window.AugmentContentAlignment
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import kotlin.math.roundToInt

/** Window height declared in the manifest (metres); the board sits on the base panel. */
private const val WINDOW_HEIGHT_M = 0.62f

private object Attach {
    const val HINT = "hint"
    const val TRAY_WHITE = "trayWhite"
    const val TRAY_BLACK = "trayBlack"
    const val FILES = "coordsFiles"
    const val RANKS = "coordsRanks"
    const val CONTEXT = "contextPanel"
}

@Composable
fun ChessApp() {
    val context = LocalContext.current
    val vm = remember { ChessViewModel.get(context) }
    LaunchedEffect(vm) { while (true) { withFrameNanos { vm.tick(it) } } }

    var reload by remember { mutableIntStateOf(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        key(reload) { BoardView(vm) }
    }

    ChessToolbar(vm)
    TopBar(vm, onRetryLoad = { reload++ })
    Overlays(vm)
}

// ---------------------------------------------------------------------- 3D view

@Composable
private fun BoardView(vm: ChessViewModel) {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    fun pxToM(px: Float): Float = with(converter) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }

    SpatialView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectSpatialTapGesture(context, TargetEntity.any()) { tap ->
                    vm.onTap(tap.targetEntity?.getName())
                }
            }
            .pointerInput(Unit) {
                detectSpatialDragGesture(
                    context = context,
                    targetedToEntity = TargetEntity.any(),
                    onDragStart = { _, v -> vm.onDragStart(v.targetEntity?.getName()) },
                    onDragEnd = { vm.onDragEnd() },
                    onDragCancel = { vm.onDragCancel() },
                ) { v ->
                    vm.onDrag(pxToM(v.dragAmount.x), -pxToM(v.dragAmount.y), pxToM(v.dragAmount.z))
                }
            },
        attachments = {
            AttachmentPanel(id = Attach.HINT) { HintLabel(vm) }
            AttachmentPanel(id = Attach.TRAY_WHITE) { TrayLabel(vm, Side.WHITE) }
            AttachmentPanel(id = Attach.TRAY_BLACK) { TrayLabel(vm, Side.BLACK) }
            AttachmentPanel(id = Attach.FILES) { CoordLabel(vm, "a   b   c   d   e   f   g   h") }
            AttachmentPanel(id = Attach.RANKS) { CoordLabel(vm, "8   7   6   5   4   3   2   1") }
            AttachmentPanel(id = Attach.CONTEXT) { ContextPanel(vm) }
        },
        initial = { content, attachments ->
            val scene = ChessScene(content, floorY = -WINDOW_HEIGHT_M / 2f)
            vm.attachScene(scene)
            attachments.entity(Attach.HINT)?.let { scene.addSceneLabel(it, Vector3(0f, 0.045f, 0.46f), pitch = -40f) }
            // contextual panel floats at the upper right of the board, facing the player (Figma 02/07/10/13)
            attachments.entity(Attach.CONTEXT)?.let { scene.addSceneLabel(it, Vector3(0.43f, 0.30f, -0.12f), pitch = -12f) }
            val trayX = ChessScene.TRAY_X0 + ChessScene.TRAY_PITCH / 2f
            val trayZ = -4f * ChessScene.TRAY_PITCH - 0.03f
            attachments.entity(Attach.TRAY_WHITE)?.let { scene.addLabel(it, Vector3(-trayX, 0.03f, trayZ)) }
            attachments.entity(Attach.TRAY_BLACK)?.let { scene.addLabel(it, Vector3(trayX, 0.03f, trayZ)) }
            attachments.entity(Attach.FILES)?.let {
                scene.addLabel(it, Vector3(0f, ChessScene.BOARD_TOP + 0.004f, ChessScene.BOARD_HALF - 0.012f), flat = true)
            }
            attachments.entity(Attach.RANKS)?.let {
                scene.addLabel(it, Vector3(-(ChessScene.BOARD_HALF - 0.012f), ChessScene.BOARD_TOP + 0.004f, 0f), flat = true, flatYaw = 90f)
            }
        },
    )
}

@Composable
private fun HintLabel(vm: ChessViewModel) {
    val text = vm.hint
    if (text.isBlank()) return
    Box(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ChessColors.Card)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text = text, color = ChessColors.Ink, style = PicoTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TrayLabel(vm: ChessViewModel, side: Side) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(ChessColors.Card).padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text = vm.trayCountText(side), color = ChessColors.Ink, style = PicoTheme.typography.bodySmall)
    }
}

@Composable
private fun CoordLabel(vm: ChessViewModel, text: String) {
    if (!vm.settings.showCoordinates) return
    Text(text = text, color = ChessColors.Muted, style = PicoTheme.typography.labelSmall, fontSize = 11.sp)
}

// ---------------------------------------------------------------------- toolbar (前侧工具栏)

@Composable
private fun ChessToolbar(vm: ChessViewModel) {
    val enabled = vm.phase == Phase.IDLE
    Toolbar(cornerSize = 32.dp) {
        ToolButton(L10n.t("ui.text_008"), enabled = enabled && vm.canUndo) { vm.undo() }
        Spacer(Modifier.width(8.dp))
        ToolButton(L10n.t("ui.text_009"), enabled = enabled && vm.canRedo) { vm.redo() }
        Spacer(Modifier.width(8.dp))
        ToolButton(L10n.t("ui.text_010"), enabled = enabled, emphasized = true) { vm.openSettings() }
        Spacer(Modifier.width(8.dp))
        ToolButton(L10n.t("ui.text_011"), enabled = enabled) { vm.openReset() }
        Spacer(Modifier.width(8.dp))
        ToolButton(L10n.t("ui.text_012"), enabled = enabled) { vm.openHelp() }
    }
}

@Composable
private fun ToolButton(text: String, enabled: Boolean, emphasized: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        size = ButtonDefaults.Regular,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ChessColors.Ink),
    ) {
        // design-style: inherited-content-color Button
        Text(text, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, size: ButtonSize = ButtonDefaults.Regular, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled, size = size,
        colors = ButtonDefaults.buttonColors(containerColor = ChessColors.Blue, contentColor = Color.White),
    ) { Text(text) } // design-style: inherited-content-color Button
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, size: ButtonSize = ButtonDefaults.Regular, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled, size = size,
        colors = if (selected) ButtonDefaults.buttonColors(containerColor = ChessColors.Blue, contentColor = Color.White)
        else ButtonDefaults.buttonColors(containerColor = ChessColors.Pill, contentColor = ChessColors.Ink),
    ) { Text(text) } // design-style: inherited-content-color Button
}

@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled, size = ButtonDefaults.Regular,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ChessColors.Ink),
    ) { Text(text) } // design-style: inherited-content-color Button
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ChessColors.Card)
            .border(1.dp, ChessColors.CardBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) { content() }
}

// ---------------------------------------------------------------------- top bar (title · save state · anchor chip)

@Composable
private fun TopBar(vm: ChessViewModel, onRetryLoad: () -> Unit) {
    Augment(
        anchor = NormalizedPoint3D.TopFront,
        alignment = AugmentContentAlignment.BottomCenter,
        offset = DpOffset3D(0.dp, (-12).dp, 0.dp),
        cornerRadius = 24.dp,
        enableMaterialBackground = false,
    ) {
        Row(modifier = Modifier.width(1240.dp), verticalAlignment = Alignment.Top) {
            Card {
                Column {
                    Text(L10n.t("ui.text_001"), color = ChessColors.Ink, style = PicoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(L10n.t("ui.text_002"), color = ChessColors.Muted, style = PicoTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            if (vm.saveState == SaveState.FAILED) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L10n.t("ui.text_046"), color = ChessColors.Ink, style = PicoTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        SecondaryButton(L10n.t("ui.text_047"), size = ButtonDefaults.Small) { vm.retrySave() }
                    }
                }
            }
            if (vm.phase == Phase.MODEL_FAILED) {
                Spacer(Modifier.width(16.dp))
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L10n.t("runtime.model.failed"), color = ChessColors.Ink, style = PicoTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        PrimaryButton(L10n.t("ui.text_047"), size = ButtonDefaults.Small) { onRetryLoad() }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val saved = vm.saveState == SaveState.SAVED
            Chip(
                text = if (saved) L10n.t("ui.text_003") else L10n.t("ui.text_045"),
                bg = if (saved) ChessColors.Pill else ChessColors.WarnBg,
                fg = if (saved) ChessColors.Muted else ChessColors.WarnFg,
            )
        }
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, color = fg, style = PicoTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
}

// ---------------------------------------------------------------------- context panel (侧上方)

@Composable
private fun ContextPanel(vm: ChessViewModel) {
    val panel = vm.panel
    val show = panel is Panel.Selected || panel is Panel.CapturePreview || panel is Panel.Promotion ||
        panel is Panel.PromotionFailed
    if (!show) return
    Card(modifier = Modifier.width(380.dp)) {
        when (panel) {
            is Panel.Selected -> SelectedPanel(vm, panel)
            is Panel.CapturePreview -> CapturePanel(vm, panel)
            is Panel.Promotion -> PromotionPanel(vm, panel)
            is Panel.PromotionFailed -> PromotionFailedPanel(vm)
            else -> {}
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, color = ChessColors.Ink, style = PicoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PanelBody(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = ChessColors.Muted, style = PicoTheme.typography.bodyMediumMultiline)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SelectedPanel(vm: ChessViewModel, panel: Panel.Selected) {
    val piece = vm.snapshot.byId[panel.pieceId] ?: return
    Column {
        PanelTitle(vm.pieceTitle(piece))
        PanelBody(if (piece.location is Location.Board) L10n.t("ui.text_040") else L10n.t("ui.text_044"))
        if (piece.location is Location.Board) {
            PrimaryButton(L10n.t("ui.text_041"), modifier = Modifier.fillMaxWidth()) { vm.storeSelected() }
            Spacer(Modifier.height(8.dp))
        }
        LinkButton(L10n.t("ui.text_018"), modifier = Modifier.fillMaxWidth()) { vm.cancelPanel() }
    }
}

@Composable
private fun CapturePanel(vm: ChessViewModel, panel: Panel.CapturePreview) {
    val attacker = vm.snapshot.byId[panel.attackerId] ?: return
    val victim = vm.snapshot.at(panel.targetSquare) ?: return
    Column {
        PanelTitle(vm.captureTitle(panel.targetSquare))
        PanelBody(vm.captureBody(attacker, victim))
        PrimaryButton(L10n.t("ui.text_017"), modifier = Modifier.fillMaxWidth()) { vm.confirmCapture() }
        Spacer(Modifier.height(8.dp))
        LinkButton(L10n.t("ui.text_018"), modifier = Modifier.fillMaxWidth()) { vm.cancelPanel() }
    }
}

@Composable
private fun PromotionPanel(vm: ChessViewModel, panel: Panel.Promotion) {
    val piece = vm.snapshot.byId[panel.pieceId] ?: return
    val busy = vm.promoting
    Column {
        PanelTitle(vm.pieceTitle(piece))
        PanelBody(if (busy) L10n.t("runtime.promotion.loading") else L10n.t("ui.text_051"))
        PrimaryButton(L10n.t("ui.text_052"), modifier = Modifier.fillMaxWidth(), enabled = !busy) { vm.promote(Kind.QUEEN) }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(L10n.t("ui.text_053"), modifier = Modifier.fillMaxWidth(), enabled = !busy) { vm.promote(Kind.ROOK) }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(L10n.t("ui.text_054"), modifier = Modifier.fillMaxWidth(), enabled = !busy) { vm.promote(Kind.BISHOP) }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(L10n.t("ui.text_055"), modifier = Modifier.fillMaxWidth(), enabled = !busy) { vm.promote(Kind.KNIGHT) }
        Spacer(Modifier.height(8.dp))
        LinkButton(L10n.t("ui.text_056"), modifier = Modifier.fillMaxWidth(), enabled = !busy) { vm.skipPromotion() }
    }
}

@Composable
private fun PromotionFailedPanel(vm: ChessViewModel) {
    val failed = vm.panel as? Panel.PromotionFailed ?: return
    Column {
        PanelTitle(L10n.t("ui.text_058"))
        PanelBody(L10n.t("ui.text_059"))
        PrimaryButton(L10n.t("ui.text_047"), modifier = Modifier.fillMaxWidth()) { vm.promote(failed.kind) }
        Spacer(Modifier.height(8.dp))
        LinkButton(L10n.t("ui.text_056"), modifier = Modifier.fillMaxWidth()) { vm.skipPromotion() }
    }
}

// ---------------------------------------------------------------------- overlays: reset dialog, settings sheet, help sheet

@Composable
private fun Overlays(vm: ChessViewModel) {
    when (val panel = vm.panel) {
        is Panel.Reset -> ResetDialog(vm)
        is Panel.Settings -> SettingsSheet(vm)
        is Panel.Help -> HelpSheet(vm, panel.firstUse)
        else -> {}
    }
}

@Composable
private fun ResetDialog(vm: ChessViewModel) {
    AlertDialog(
        onDismissRequest = { vm.cancelPanel() },
        title = { Text(L10n.t("ui.text_031"), color = ChessColors.Ink, style = PicoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        content = { Text(L10n.t("ui.text_032"), color = ChessColors.Muted, style = PicoTheme.typography.bodyMediumMultiline) },
        buttons = {
            Column(modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(L10n.t("ui.text_033"), modifier = Modifier.fillMaxWidth()) { vm.resetStandard() }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(L10n.t("ui.text_034"), modifier = Modifier.fillMaxWidth()) { vm.clearBoard() }
                Spacer(Modifier.height(8.dp))
                LinkButton(L10n.t("ui.text_018"), modifier = Modifier.fillMaxWidth()) { vm.cancelPanel() }
            }
        },
    )
}

@Composable
private fun SettingsSheet(vm: ChessViewModel) {
    val d = vm.draft
    Sheet(
        onDismissRequest = { vm.cancelSettings() },
        title = { Text(L10n.t("ui.text_010"), color = ChessColors.Ink, style = PicoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        bottom = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SecondaryButton(L10n.t("ui.text_018")) { vm.cancelSettings() }
                Spacer(Modifier.width(12.dp))
                PrimaryButton(L10n.t("ui.text_030")) { vm.applySettings() }
            }
        },
    ) {
        Column(modifier = Modifier.width(520.dp)) {
            Text(L10n.t("ui.text_022"), color = ChessColors.Muted, style = PicoTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(L10n.tf("runtime.board.scale", "percent" to d.scalePercent), color = ChessColors.Ink, style = PicoTheme.typography.bodyLarge)
            Slider(
                value = d.scalePercent.toFloat(),
                onValueChange = { v -> vm.updateDraft { it.copy(scalePercent = v.roundToInt()) } },
                valueRange = 80f..140f,
            )
            Text(L10n.t("ui.text_024"), color = ChessColors.Muted, style = PicoTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            SettingSwitch(L10n.t("ui.text_025"), d.showCoordinates) { v -> vm.updateDraft { it.copy(showCoordinates = v) } }
            SettingSwitch(L10n.t("ui.text_026"), d.moveSound) { v -> vm.updateDraft { it.copy(moveSound = v) } }
            SettingSwitch(L10n.t("ui.text_027"), d.reduceMotion) { v -> vm.updateDraft { it.copy(reduceMotion = v) } }
            Spacer(Modifier.height(12.dp))
            Text(L10n.t("ui.text_028"), color = ChessColors.Ink, style = PicoTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (deg in listOf(0, 90, 180, 270)) {
                    SecondaryButton("$deg°", selected = d.yawDegrees == deg, size = ButtonDefaults.Small) { vm.updateDraft { it.copy(yawDegrees = deg) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(L10n.t("ui.text_029"), color = ChessColors.Ink, style = PicoTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (cm in listOf(-1, 0, 1)) {
                    val label = if (cm > 0) "+ $cm cm" else if (cm < 0) "− ${-cm} cm" else "0 cm"
                    SecondaryButton(label, selected = d.heightOffsetCm == cm, size = ButtonDefaults.Small) { vm.updateDraft { it.copy(heightOffsetCm = cm) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.t("ui.text_065"), color = ChessColors.Ink, style = PicoTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                SecondaryButton(L10n.t("ui.text_066"), selected = L10n.locale == L10n.ZH, size = ButtonDefaults.Small) { vm.setLocale(L10n.ZH) }
                SecondaryButton(L10n.t("ui.text_067"), selected = L10n.locale == L10n.EN, size = ButtonDefaults.Small) { vm.setLocale(L10n.EN) }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ChessColors.Ink, style = PicoTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun HelpSheet(vm: ChessViewModel, firstUse: Boolean) {
    Sheet(
        onDismissRequest = { vm.dismissHelp() },
        title = { Text(L10n.t("ui.text_035"), color = ChessColors.Ink, style = PicoTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        bottom = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(if (firstUse) L10n.t("ui.text_037") else L10n.t("ui.text_030")) { vm.closeHelp() }
            }
        },
    ) {
        Column(modifier = Modifier.width(520.dp)) {
            Text(L10n.t("ui.text_069"), color = ChessColors.Ink, style = PicoTheme.typography.bodyLargeMultiline)
            Spacer(Modifier.height(12.dp))
            Text(L10n.t("ui.text_006"), color = ChessColors.Muted, style = PicoTheme.typography.bodyMedium)
        }
    }
}

@Suppress("unused")
private fun Settings.describe(): String = "scale=$scalePercent yaw=$yawDegrees height=$heightOffsetCm"
