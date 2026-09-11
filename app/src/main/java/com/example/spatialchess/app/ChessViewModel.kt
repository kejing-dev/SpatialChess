package com.example.spatialchess.app

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.spatialchess.data.BoardStore
import com.example.spatialchess.data.Settings
import com.example.spatialchess.data.SettingsStore
import com.example.spatialchess.i18n.L10n
import com.example.spatialchess.model.BoardState
import com.example.spatialchess.model.Change
import com.example.spatialchess.model.CommandResult
import com.example.spatialchess.model.Kind
import com.example.spatialchess.model.Location
import com.example.spatialchess.model.PieceState
import com.example.spatialchess.model.Side
import com.example.spatialchess.model.Snapshot
import com.example.spatialchess.model.Square
import com.example.spatialchess.scene.ChessScene
import com.example.spatialchess.scene.DropCandidate
import com.pico.spatial.core.math.Vector3

enum class Phase { LOADING, MODEL_FAILED, ONBOARDING, IDLE, GRABBED, ANIMATING }

enum class SaveState { SAVED, UNSAVED, FAILED }

/** Which contextual panel / overlay is open. Only one at a time (PRD chapter 7). */
sealed class Panel {
    object None : Panel()
    data class Selected(val pieceId: String) : Panel()
    data class CapturePreview(val attackerId: String, val targetSquare: Int) : Panel()
    data class Promotion(val pieceId: String) : Panel()
    data class PromotionFailed(val pieceId: String, val kind: Kind) : Panel()
    data class Help(val firstUse: Boolean) : Panel()
    object Settings : Panel()
    object Reset : Panel()
}

/**
 * Application state machine (PRD chapter 8): Loading → Placement/Idle → Grabbed/Selected →
 * TargetPreview → Animating → Commit → Idle. Data lives in [BoardState]; the scene only mirrors it.
 */
class ChessViewModel private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "SpatialChess.VM"
        @Volatile private var instance: ChessViewModel? = null
        fun get(context: Context): ChessViewModel =
            instance ?: synchronized(this) { instance ?: ChessViewModel(context.applicationContext).also { instance = it } }
    }

    private val boardStore = BoardStore(appContext)
    private val settingsStore = SettingsStore(appContext)
    private val sounds = Sounds(appContext)

    var settings: Settings by mutableStateOf(settingsStore.load()); private set
    private var board: BoardState = BoardState()
    var snapshot: Snapshot by mutableStateOf(board.current); private set
    var phase: Phase by mutableStateOf(Phase.LOADING); private set
    var panel: Panel by mutableStateOf(Panel.None); private set
    var selectedId: String? by mutableStateOf(null); private set
    var hint: String by mutableStateOf(""); private set
    var saveState: SaveState by mutableStateOf(SaveState.SAVED); private set
    var canUndo: Boolean by mutableStateOf(false); private set
    var canRedo: Boolean by mutableStateOf(false); private set
    var draft: Settings by mutableStateOf(settings); private set   // settings sheet working copy
    var promoting: Boolean by mutableStateOf(false); private set
    var loadNotice: String? by mutableStateOf(null); private set

    private var scene: ChessScene? = null
    private var restoredFromBackup = false
    private var busy = false            // input lock during commits (PRD: 只保留首个有效命令)

    init {
        L10n.init(appContext, settings.locale)
        Log.i(TAG, "locale: saved=${settings.locale} system=${java.util.Locale.getDefault()} -> ${L10n.locale}")
        when (val r = boardStore.load()) {
            is BoardStore.LoadResult.Loaded -> { board = r.state; restoredFromBackup = r.fromBackup }
            BoardStore.LoadResult.Empty -> board = BoardState()
            BoardStore.LoadResult.Corrupted -> { board = BoardState(); loadNotice = "corrupted" }
        }
        snapshot = board.current
        refreshFlags()
        hint = L10n.t("runtime.loading")
    }

    // ------------------------------------------------------------------ scene lifecycle

    /** Called from SpatialView.initial; the scene is rebuilt whenever the view is (re)created. */
    suspend fun attachScene(newScene: ChessScene) {
        scene?.destroy()
        scene = newScene
        phase = Phase.LOADING
        hint = L10n.t("runtime.loading")
        val ok = newScene.load()
        if (!ok) {
            phase = Phase.MODEL_FAILED
            hint = L10n.t("runtime.model.failed")
            return
        }
        newScene.applySettings(settings.scalePercent, settings.yawDegrees, settings.heightOffsetCm)
        newScene.syncAll(snapshot)
        if (!settings.onboardingDone) {
            phase = Phase.ONBOARDING; panel = Panel.Help(firstUse = true); hint = L10n.t("ui.text_068")
        } else {
            phase = Phase.IDLE; panel = Panel.None; hint = L10n.t("ui.text_068")
        }
        if (restoredFromBackup) { restoredFromBackup = false; hint = L10n.t("ui.text_003") }
        Log.i(TAG, "scene ready, phase=$phase pieces=${snapshot.pieces.size}")
    }

    fun tick(nowNs: Long) { scene?.tick(nowNs) }

    fun retryLoad() { /* the SpatialView re-creates the scene; nothing else to do here */ }

    // ------------------------------------------------------------------ helpers

    private fun refreshFlags() { canUndo = board.canUndo; canRedo = board.canRedo }

    private val modal: Boolean
        get() = panel is Panel.Settings || panel is Panel.Reset || panel is Panel.Help ||
            panel is Panel.Promotion || panel is Panel.PromotionFailed

    private val interactive: Boolean get() = phase == Phase.IDLE && !modal && !busy

    fun pieceName(p: PieceState): String = L10n.pieceName(p.side.key, p.kind.key)

    fun pieceTitle(p: PieceState): String = when (val loc = p.location) {
        is Location.Board -> pieceName(p) + " · " + Square.name(loc.square)
        is Location.Tray -> pieceName(p) + " · " + trayWord()
    }

    /** "收纳区" / "Tray" derived from ui.text_043 so the key set stays untouched. */
    private fun trayWord(): String = L10n.t("ui.text_043").substringAfterLast("·").trim()

    fun captureTitle(square: Int): String = L10n.t("ui.text_015").replace("d5", Square.name(square))

    fun captureBody(attacker: PieceState, victim: PieceState): String {
        val base = L10n.t("ui.text_016")
        return if (L10n.isChinese) base.replace("黑方兵", pieceName(victim)).replace("白方兵", pieceName(attacker))
        else base.replace("black pawn", pieceName(victim).lowercase()).replace("white pawn", pieceName(attacker).lowercase())
    }

    fun trayCountText(side: Side): String {
        val sym = if (side == Side.WHITE) "○ " else "● "
        return sym + L10n.tf("runtime.tray.count", "side" to L10n.t(side.key), "count" to snapshot.trayCount(side), "capacity" to Location.TRAY_CAPACITY)
    }

    private fun idleHint() { hint = L10n.t("ui.text_068") }

    // ------------------------------------------------------------------ taps (assist input)

    fun onTap(entityName: String?) {
        Log.d(TAG, "tap on $entityName phase=$phase panel=$panel")
        if (entityName == null) return
        if (!interactive) return
        when {
            entityName.startsWith("piece:") -> tapPiece(entityName.removePrefix("piece:"))
            entityName.startsWith("sq:") -> entityName.removePrefix("sq:").toIntOrNull()?.let { tapSquare(it) }
            entityName.startsWith("tray:") -> runCatching { Side.valueOf(entityName.removePrefix("tray:")) }.getOrNull()?.let { tapTray(it) }
        }
    }

    private fun tapPiece(id: String) {
        val piece = snapshot.byId[id] ?: return
        val sel = selectedId?.let { snapshot.byId[it] }
        when {
            sel == null -> select(id)
            sel.id == id -> deselect()
            sel.side == piece.side -> select(id)
            sel.location is Location.Tray -> select(id)          // a tray piece cannot capture
            else -> previewCapture(sel, (piece.location as Location.Board).square)
        }
    }

    private fun tapSquare(square: Int) {
        val sel = selectedId?.let { snapshot.byId[it] } ?: return
        val occupant = snapshot.at(square)
        when {
            occupant == null -> commit(board.move(sel.id, square))
            occupant.side == sel.side -> {
                hint = L10n.tf("runtime.target.blocked", "side" to L10n.t(sel.side.key))
                scene?.showTarget(square, capture = false, blocked = true)
            }
            sel.location is Location.Tray -> hint = L10n.tf("runtime.target.blocked", "side" to L10n.t(occupant.side.key))
            else -> previewCapture(sel, square)
        }
    }

    private fun tapTray(side: Side) {
        val sel = selectedId?.let { snapshot.byId[it] } ?: return
        if (sel.side == side && sel.location is Location.Board) storeSelected()
    }

    fun select(id: String) {
        val piece = snapshot.byId[id] ?: return
        selectedId = id
        panel = Panel.Selected(id)
        scene?.setSelected(id, snapshot)
        scene?.showTarget(null, false)
        hint = when (val loc = piece.location) {
            is Location.Board -> L10n.tf("runtime.piece.selected", "side" to L10n.t(piece.side.key), "piece" to L10n.t(piece.kind.key), "square" to Square.name(loc.square))
            is Location.Tray -> L10n.t("ui.text_042").let { s ->
                if (L10n.isChinese) s.replace("黑方兵", pieceName(piece)) else s.replace("Black pawn", pieceName(piece))
            }
        }
    }

    fun deselect() {
        selectedId = null
        if (panel is Panel.Selected || panel is Panel.CapturePreview) panel = Panel.None
        scene?.setSelected(null, snapshot)
        scene?.showTarget(null, false)
        idleHint()
    }

    private fun previewCapture(attacker: PieceState, square: Int) {
        panel = Panel.CapturePreview(attacker.id, square)
        scene?.showTarget(square, capture = true)
        val victim = snapshot.at(square) ?: return
        hint = captureTitle(square) + " · " + captureBody(attacker, victim)
    }

    fun confirmCapture() {
        val p = panel as? Panel.CapturePreview ?: return
        commit(board.capture(p.attackerId, p.targetSquare))
    }

    fun cancelPanel() {
        when (panel) {
            is Panel.CapturePreview -> selectedId?.let { select(it) }
            is Panel.Selected -> deselect()
            is Panel.Promotion, is Panel.PromotionFailed -> { panel = Panel.None; idleHint() }
            else -> { panel = Panel.None; idleHint() }
        }
    }

    fun storeSelected() {
        val id = selectedId ?: return
        commit(board.store(id))
    }

    // ------------------------------------------------------------------ commit pipeline

    private fun commit(result: CommandResult, afterDone: (() -> Unit)? = null) {
        when (result) {
            is CommandResult.Rejected -> {
                hint = if (result.reasonKey == "runtime.target.blocked") L10n.tf(result.reasonKey, "side" to (selectedId?.let { snapshot.byId[it]?.side }?.let { L10n.t(it.key) } ?: ""))
                else L10n.t(result.reasonKey)
                Log.w(TAG, "command rejected: ${result.reasonKey}")
            }
            is CommandResult.Ok -> {
                busy = true
                val change = result.change
                val movedId = when (change) { is Change.Move -> change.id; is Change.Capture -> change.attackerId; else -> null }
                selectedId = null
                if (panel is Panel.Selected || panel is Panel.CapturePreview || panel is Panel.Reset) panel = Panel.None
                scene?.setSelected(null, snapshot)
                scene?.showTarget(null, false)
                val after = board.current
                snapshot = after
                refreshFlags()
                phase = Phase.ANIMATING
                val sc = scene
                if (sc == null) { finishCommit(change, movedId, afterDone); return }
                sc.applyChange(after, change, settings.reduceMotion) { finishCommit(change, movedId, afterDone) }
            }
        }
    }

    private fun finishCommit(change: Change, movedId: String?, afterDone: (() -> Unit)?) {
        busy = false
        phase = Phase.IDLE
        if (settings.moveSound) {
            val stored = change is Change.Capture || (change is Change.Move && change.to is Location.Tray)
            val at = when (change) {
                is Change.Move -> change.id
                is Change.Capture -> change.attackerId
                is Change.Promote -> change.id
                else -> null
            }
            sounds.play(if (stored) Sounds.Kind.STORE else Sounds.Kind.MOVE, at?.let { scene?.pivotOf(it) })
        }
        hint = when (change) {
            is Change.Move -> when (val to = change.to) {
                is Location.Board -> L10n.tf("runtime.move.snapped", "square" to Square.name(to.square))
                is Location.Tray -> {
                    val p = snapshot.byId[change.id]
                    if (p != null) L10n.tf("runtime.move.captured", "capturedSide" to L10n.t(p.side.key), "capturedPiece" to L10n.t(p.kind.key), "side" to "", "piece" to "", "square" to "")
                        .substringBefore(if (L10n.isChinese) "，" else ".").let { if (L10n.isChinese) it else "$it." }
                    else L10n.t("ui.text_068")
                }
            }
            is Change.Capture -> {
                val a = snapshot.byId[change.attackerId]; val v = snapshot.byId[change.capturedId]
                if (a != null && v != null) L10n.tf("runtime.move.captured",
                    "capturedSide" to L10n.t(v.side.key), "capturedPiece" to L10n.t(v.kind.key),
                    "side" to L10n.t(a.side.key), "piece" to L10n.t(a.kind.key), "square" to Square.name(change.to))
                else L10n.t("ui.text_068")
            }
            is Change.Promote -> L10n.tf("runtime.promotion.complete", "square" to Square.name(change.square),
                "side" to (snapshot.byId[change.id]?.side?.let { L10n.t(it.key) } ?: ""), "piece" to L10n.t(change.toKind.key))
            else -> L10n.t("ui.text_068")
        }
        save()
        // promotion is a separate transaction that starts after the move is committed (PRD chapter 12)
        if (movedId != null && board.isPromotionSquare(movedId)) {
            panel = Panel.Promotion(movedId)
            hint = L10n.t("ui.text_049")
        }
        afterDone?.invoke()
    }

    fun save() {
        val r = boardStore.save(board)
        saveState = if (r.isSuccess) SaveState.SAVED else SaveState.FAILED
    }

    fun retrySave() = save()

    // ------------------------------------------------------------------ undo / redo / reset

    fun undo() { if (!interactive && phase != Phase.IDLE) return; if (busy) return; applyHistory(board.undo()) }
    fun redo() { if (busy) return; applyHistory(board.redo()) }

    private fun applyHistory(change: Change?) {
        if (change == null) return
        selectedId = null; panel = Panel.None
        scene?.setSelected(null, snapshot); scene?.showTarget(null, false)
        busy = true; phase = Phase.ANIMATING
        val after = board.current
        snapshot = after; refreshFlags()
        val sc = scene
        if (sc == null) { busy = false; phase = Phase.IDLE; save(); return }
        sc.applyChange(after, change, settings.reduceMotion) { busy = false; phase = Phase.IDLE; idleHint(); save() }
    }

    fun openReset() { if (busy) return; deselect(); panel = Panel.Reset }
    fun resetStandard() { panel = Panel.None; commit(board.reset()) }
    fun clearBoard() { panel = Panel.None; commit(board.clear()) }

    // ------------------------------------------------------------------ promotion

    fun promote(kind: Kind) {
        val p = panel as? Panel.Promotion ?: (panel as? Panel.PromotionFailed)?.let { Panel.Promotion(it.pieceId) } ?: return
        val piece = snapshot.byId[p.pieceId] ?: return
        if (promoting) return                       // 连续点击只生成一次
        promoting = true
        hint = L10n.t("runtime.promotion.loading")
        val sc = scene
        val created = sc?.ensurePieceEntity(piece.id, piece.side, kind)
        if (sc != null && created == null) {
            promoting = false
            panel = Panel.PromotionFailed(piece.id, kind)
            hint = L10n.t("ui.text_058")
            return
        }
        val result = board.promote(piece.id, kind)
        promoting = false
        if (result is CommandResult.Ok) {
            snapshot = board.current; refreshFlags(); panel = Panel.None
            finishCommit(result.change, null, null)
        } else {
            panel = Panel.PromotionFailed(piece.id, kind)
        }
    }

    fun skipPromotion() { panel = Panel.None; idleHint() }

    // ------------------------------------------------------------------ help / onboarding

    fun openHelp() { if (busy) return; deselect(); panel = Panel.Help(firstUse = false) }

    /** "开始摆棋" / "完成" on the help sheet: completes onboarding on first use. */
    fun closeHelp() {
        val wasFirst = (panel as? Panel.Help)?.firstUse == true
        panel = Panel.None
        if (wasFirst) {
            settings = settings.copy(onboardingDone = true); settingsStore.save(settings)
            phase = Phase.IDLE; idleHint()
        } else idleHint()
    }

    /** Sheet dismissed without the button (system teardown, close icon): keep first-use state. */
    fun dismissHelp() {
        val wasFirst = (panel as? Panel.Help)?.firstUse == true
        if (wasFirst) return
        panel = Panel.None
        idleHint()
    }

    // ------------------------------------------------------------------ settings (摆放与显示)

    fun openSettings() {
        if (busy) return
        deselect()
        draft = settings
        panel = Panel.Settings
        hint = L10n.t("ui.text_021")
    }

    fun updateDraft(transform: (Settings) -> Settings) {
        draft = transform(draft).let { it.copy(scalePercent = it.scalePercent.coerceIn(80, 140)) }
        scene?.applySettings(draft.scalePercent, draft.yawDegrees, draft.heightOffsetCm)
    }

    fun setLocale(locale: String) {
        Log.i(TAG, "setLocale($locale)", Throwable("trace"))
        L10n.switchLocale(locale)
        settings = settings.copy(locale = L10n.locale)
        draft = draft.copy(locale = L10n.locale)
        settingsStore.save(settings)
        if (panel is Panel.Settings) hint = L10n.t("ui.text_021") else idleHint()
    }

    fun applySettings() {
        settings = draft.copy(locale = settings.locale, onboardingDone = settings.onboardingDone)
        settingsStore.save(settings)
        scene?.applySettings(settings.scalePercent, settings.yawDegrees, settings.heightOffsetCm)
        panel = Panel.None
        idleHint()
    }

    fun cancelSettings() {
        draft = settings
        scene?.applySettings(settings.scalePercent, settings.yawDegrees, settings.heightOffsetCm)
        panel = Panel.None
        idleHint()
    }

    // ------------------------------------------------------------------ grab & drop (default input)

    fun onDragStart(entityName: String?) {
        if (entityName == null || !entityName.startsWith("piece:")) return
        if (!interactive) return
        val id = entityName.removePrefix("piece:")
        if (snapshot.byId[id] == null) return
        selectedId = id
        panel = Panel.None
        phase = Phase.GRABBED
        scene?.beginGrab(id, snapshot)
        hint = L10n.t("ui.text_070")
    }

    fun onDrag(dx: Float, dy: Float, dz: Float) {
        if (phase != Phase.GRABBED) return
        val sc = scene ?: return
        when (val c = sc.moveGrabbed(Vector3(dx, dy, dz), snapshot)) {
            is DropCandidate.Square -> {
                val occ = snapshot.at(c.index)
                val me = selectedId?.let { snapshot.byId[it] }
                val blocked = occ != null && me != null && occ.side == me.side && occ.id != me.id
                sc.showTarget(c.index, capture = occ != null && !blocked && occ.id != me?.id, blocked = blocked)
                hint = if (blocked) L10n.tf("runtime.target.blocked", "side" to L10n.t(me!!.side.key))
                else L10n.t("ui.text_075")
            }
            is DropCandidate.Tray -> { sc.showTarget(null, false); hint = L10n.t("ui.text_041") }
            DropCandidate.None -> { sc.showTarget(null, false); hint = L10n.t("ui.text_070") }
        }
    }

    fun onDragEnd() {
        if (phase != Phase.GRABBED) return
        val sc = scene ?: return
        val id = selectedId ?: return
        val me = snapshot.byId[id] ?: return
        val candidate = sc.currentGrabCandidate(snapshot)
        sc.showTarget(null, false)
        phase = Phase.IDLE
        when (candidate) {
            is DropCandidate.Square -> {
                val occ = snapshot.at(candidate.index)
                when {
                    occ == null -> { selectedId = null; sc.endGrab(ChessScene.squareCenter(candidate.index)) { commit(board.move(id, candidate.index)) } }
                    occ.id == id -> returnPiece(sc)
                    occ.side == me.side -> returnPiece(sc, L10n.tf("runtime.target.blocked", "side" to L10n.t(me.side.key)))
                    me.location is Location.Tray -> returnPiece(sc, L10n.tf("runtime.target.blocked", "side" to L10n.t(occ.side.key)))
                    else -> { selectedId = null; sc.endGrab(ChessScene.squareCenter(candidate.index)) { commit(board.capture(id, candidate.index)) } }
                }
            }
            is DropCandidate.Tray -> {
                if (me.location is Location.Board && candidate.side == me.side && snapshot.freeTraySlot(me.side) != null) {
                    selectedId = null
                    sc.endGrab(null) { commit(board.store(id)) }
                } else returnPiece(sc, if (snapshot.freeTraySlot(me.side) == null) L10n.t("runtime.tray.full") else L10n.t("runtime.target.invalid"))
            }
            DropCandidate.None -> returnPiece(sc, L10n.t("runtime.target.invalid"))
        }
    }

    private fun returnPiece(sc: ChessScene, message: String = L10n.t("runtime.target.invalid")) {
        selectedId = null
        sc.endGrab(null) { sc.setSelected(null, snapshot); hint = message }
    }

    fun onDragCancel() {
        if (phase != Phase.GRABBED) return
        scene?.cancelGrab()
        scene?.showTarget(null, false)
        scene?.setSelected(null, snapshot)
        selectedId = null
        phase = Phase.IDLE
        hint = L10n.t("runtime.tracking.lost")
    }

    // ------------------------------------------------------------------ debug hooks (adb broadcast)

    fun debug(cmd: String, arg: String) {
        Log.i(TAG, "debug cmd=$cmd arg=$arg")
        val sq = Square.parse(arg)
        when (cmd) {
            "view" -> {
                val p = arg.split(",").map { it.trim().toFloatOrNull() ?: 0f }
                scene?.setOrbit(p.getOrElse(0) { 0f }, p.getOrElse(1) { 0f }, p.getOrElse(2) { 1f }.takeIf { it > 0f } ?: 1f)
            }
            "select" -> sq?.let { snapshot.at(it)?.let { p -> if (interactive) select(p.id) } }
            "selectId" -> if (interactive && snapshot.byId[arg] != null) select(arg)
            "tap" -> if (sq != null) onTap(snapshot.at(sq)?.let { "piece:${it.id}" } ?: "sq:$sq") else onTap(arg)
            "square" -> sq?.let { onTap("sq:$it") }
            "store" -> storeSelected()
            "cancel" -> cancelPanel()
            "confirm" -> if (panel is Panel.CapturePreview) confirmCapture()
            "undo" -> undo()
            "redo" -> redo()
            "reset" -> openReset()
            "resetStandard" -> resetStandard()
            "clear" -> clearBoard()
            "settings" -> openSettings()
            "apply" -> applySettings()
            "help" -> openHelp()
            "closeHelp" -> closeHelp()
            "promote" -> runCatching { Kind.valueOf(arg.uppercase()) }.getOrNull()?.let { promote(it) }
            "skipPromotion" -> skipPromotion()
            "lang" -> setLocale(if (arg.startsWith("zh")) L10n.ZH else L10n.EN)
            "orient" -> updateDraft { it.copy(yawDegrees = arg.toIntOrNull() ?: 0) }.also { if (panel !is Panel.Settings) applySettings() }
            "scale" -> updateDraft { it.copy(scalePercent = arg.toIntOrNull() ?: 100) }.also { if (panel !is Panel.Settings) applySettings() }
            "height" -> updateDraft { it.copy(heightOffsetCm = arg.toIntOrNull() ?: 0) }.also { if (panel !is Panel.Settings) applySettings() }
            "coords" -> updateDraft { it.copy(showCoordinates = arg == "on") }.also { if (panel !is Panel.Settings) applySettings() }
            "reduceMotion" -> updateDraft { it.copy(reduceMotion = arg == "on") }.also { if (panel !is Panel.Settings) applySettings() }
            "failsave" -> { boardStore.simulateFailure = arg == "on"; if (arg == "on") save() }
            "save" -> save()
            "drag" -> {
                // "e2 e4": simulate grab → move → release through the same code path as real input
                val parts = arg.split(" ")
                val from = parts.getOrNull(0)?.let { Square.parse(it) }; val to = parts.getOrNull(1)?.let { Square.parse(it) }
                val sc = scene
                if (from != null && to != null && sc != null) {
                    val piece = snapshot.at(from)
                    if (piece != null) {
                        onDragStart("piece:${piece.id}")
                        val delta = sc.boardToScene(ChessScene.squareCenter(to)) - sc.boardToScene(ChessScene.squareCenter(from))
                        onDrag(delta.x, 0.03f, delta.z)
                        onDragEnd()
                    }
                }
            }
            "pieces" -> {
                Log.i(TAG, "pieces:\n" + snapshot.pieces.sortedBy { it.id }.joinToString("\n") { p ->
                    val loc = when (val l = p.location) { is Location.Board -> Square.name(l.square); is Location.Tray -> "tray ${l.side} ${l.slot}" }
                    "${p.id} ${p.side} ${p.kind} @ $loc" })
                Log.i(TAG, "pivots:\n" + (scene?.dumpPieces() ?: "no scene"))
            }
            "state" -> Log.i(TAG, "phase=$phase panel=$panel selected=$selectedId save=$saveState hint=$hint board=${snapshot.boardCount()} trays=${snapshot.trayCount(Side.WHITE)}/${snapshot.trayCount(Side.BLACK)} undo=$canUndo redo=$canRedo hist=${board.historySize}")
            "wipe" -> { settings = Settings(); settingsStore.save(settings) }
            else -> Log.w(TAG, "unknown debug cmd $cmd")
        }
    }
}
