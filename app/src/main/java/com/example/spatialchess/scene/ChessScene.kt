package com.example.spatialchess.scene

import android.util.Log
import com.example.spatialchess.model.Change
import com.example.spatialchess.model.Kind
import com.example.spatialchess.model.Location
import com.example.spatialchess.model.Side
import com.example.spatialchess.model.Snapshot
import com.example.spatialchess.model.Square
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where a grabbed piece would land if released now. */
sealed class DropCandidate {
    data class Square(val index: Int) : DropCandidate()
    data class Tray(val side: Side) : DropCandidate()
    object None : DropCandidate()
}

/**
 * Owns the 3D hierarchy of the board (PRD chapter 9 "逻辑场景层级"):
 *
 * ```
 * sceneRoot (debug orbit only) → boardRoot (scale · yaw · height) → boardPivot → Chess_Board mesh
 *                                                                → piece pivots[32] → orient → model
 *                                                                → SquareTargets[64] (colliders)
 *                                                                → tray bases, markers
 * ```
 * All world transforms are applied to parents only; squares and tray slots use board-local
 * coordinates in model units (metres of the USDZ before [MODEL_SCALE]).
 */
class ChessScene(private val content: SpatialViewContent, private val floorY: Float) {

    companion object {
        private const val TAG = "SpatialChess.Scene"
        const val SQ = 0.0392f              // square pitch in model units
        const val BOARD_TOP = 0.0324f       // top surface of the board mesh
        const val BOARD_HALF = 0.1948f      // half width of the board mesh incl. rim
        const val MODEL_SCALE = 1.54f       // 0.39 m model → 0.60 m real board (PRD: 60 x 60 cm base)
        const val TRAY_PITCH = 0.036f
        const val TRAY_TOP = 0.004f
        val TRAY_X0 = BOARD_HALF + 0.022f + TRAY_PITCH / 2f
        const val LIFT_SELECTED = 0.0055f   // ≈ 8 mm real
        const val LIFT_ANIM = 0.02f         // ≈ 3 cm real
        const val CAPTURE_STORE_MS = 240f
        const val MOVE_MS = 220f

        private val PIECE_HEIGHT = mapOf(
            Kind.PAWN to 0.039f, Kind.ROOK to 0.047f, Kind.KNIGHT to 0.053f,
            Kind.BISHOP to 0.064f, Kind.QUEEN to 0.072f, Kind.KING to 0.084f,
        )

        /** Logical piece id → prim name inside chess.usdz (standard opening layout of the asset). */
        val MODEL_NAMES: Map<String, String> = mapOf(
            "w_rook_a" to "Rook_White_001", "w_rook_h" to "Rook_White",
            "w_knight_b" to "Knight_White_001", "w_knight_g" to "Knight_White",
            "w_bishop_c" to "Bishop_White_001", "w_bishop_f" to "Bishop_White",
            "w_queen" to "Queen_White", "w_king" to "King_White",
            "w_pawn_a" to "Pawn_White_007", "w_pawn_b" to "Pawn_White_006", "w_pawn_c" to "Pawn_White_005",
            "w_pawn_d" to "Pawn_White_004", "w_pawn_e" to "Pawn_White_003", "w_pawn_f" to "Pawn_White_002",
            "w_pawn_g" to "Pawn_White_001", "w_pawn_h" to "Pawn_White",
            "b_rook_a" to "Rook_Black_001", "b_rook_h" to "Rook_Black",
            "b_knight_b" to "Knight_Black_001", "b_knight_g" to "Knight_Black",
            "b_bishop_c" to "Bishop_Black_001", "b_bishop_f" to "Bishop_Black",
            "b_queen" to "Queen_Black", "b_king" to "King_Black",
            "b_pawn_a" to "Pawn_Black_007", "b_pawn_b" to "Pawn_Black_006", "b_pawn_c" to "Pawn_Black_005",
            "b_pawn_d" to "Pawn_Black_004", "b_pawn_e" to "Pawn_Black_003", "b_pawn_f" to "Pawn_Black_002",
            "b_pawn_g" to "Pawn_Black_001", "b_pawn_h" to "Pawn_Black",
        )

        fun squareCenter(index: Int): Vector3 =
            Vector3((Square.file(index) - 3.5f) * SQ, BOARD_TOP, -(Square.rank(index) - 3.5f) * SQ)

        fun traySlotCenter(side: Side, slot: Int): Vector3 {
            val sign = if (side == Side.WHITE) -1f else 1f
            val col = slot / 8
            val row = slot % 8
            return Vector3(sign * (TRAY_X0 + col * TRAY_PITCH), TRAY_TOP, (row - 3.5f) * TRAY_PITCH)
        }

        fun locationCenter(loc: Location): Vector3 = when (loc) {
            is Location.Board -> squareCenter(loc.square)
            is Location.Tray -> traySlotCenter(loc.side, loc.slot)
        }
    }

    private class Tween(
        val entity: Entity, val from: Vector3, val to: Vector3, val durationMs: Float, val lift: Float,
        val onDone: (() -> Unit)?,
    ) { var startNs = -1L }

    val sceneRoot = Entity().apply { setName("sceneRoot") }
    val boardRoot = Entity().apply { setName("boardRoot") }
    private val boardPivot = Entity().apply { setName("boardPivot") }
    private var modelRoot: Entity? = null
    private var primitivesRoot: Entity? = null
    private lateinit var boxMesh: MeshResource
    private lateinit var cylinderMesh: MeshResource
    private lateinit var captureInner: Entity
    private val pivots = HashMap<String, Entity>()             // pieceId → active pivot
    private val pivotKinds = HashMap<String, Kind>()           // kind represented by the active pivot
    private val sparePivots = HashMap<Pair<String, Kind>, Entity>()
    private val prototypes = HashMap<Pair<Side, Kind>, Entity>()
    private val squares = arrayOfNulls<Entity>(64)
    private lateinit var selectionRing: Entity
    private lateinit var targetMarker: Entity
    private lateinit var captureMarker: Entity
    private lateinit var blockedMarker: Entity
    private var upConversion = EulerAngles()
    private val orients = ArrayList<Entity>()
    private val tweens = ArrayList<Tween>()
    private val piecePositions = HashMap<String, Vector3>()    // committed board-local position per piece
    private class Label(val entity: Entity, val local: Vector3, val flat: Boolean, val flatYaw: Float)
    private val labels = ArrayList<Label>()

    var modelScale = MODEL_SCALE; private set
    var userScale = 1f; private set
    var yawDegrees = 0f; private set
    var heightOffset = 0f; private set
    private var orbitYaw = 0f
    private var orbitPitch = 0f
    private var orbitZoom = 1f

    val isAnimating: Boolean get() = tweens.isNotEmpty()

    // ------------------------------------------------------------------ build

    suspend fun load(): Boolean {
        val t0 = System.currentTimeMillis()
        val model = try {
            Entity.loadSuspend("asset://chess.usdz")
        } catch (e: Exception) {
            Log.e(TAG, "model load failed", e); return false
        }
        modelRoot = model
        content.addEntity(sceneRoot)
        sceneRoot.transform().setPosition(Vector3(0f, floorY, 0f))
        sceneRoot.addChild(boardRoot)
        boardRoot.addChild(boardPivot)

        val boardMesh = model.findEntity("Chess_Board")
        if (boardMesh == null) { Log.e(TAG, "Chess_Board prim missing"); return false }
        // The USDZ is authored Z-up; the loader keeps child prims Z-up and only rotates the root,
        // so measure the board mesh in its own space to decide whether an up-axis fix is needed.
        val bb = boardMesh.getVisualBounds(boardMesh, true, false)
        val zUp = bb.size.z < bb.size.y
        upConversion = if (zUp) EulerAngles(pitch = -90f) else EulerAngles()
        Log.i(TAG, "board local bounds size=${bb.size} min=${bb.min} zUp=$zUp")

        // board mesh
        boardMesh.removeFromParent()
        val boardOrient = Entity().apply { setName("boardOrient") }
        boardOrient.transform().setEulerAngles(upConversion)
        orients.add(boardOrient)
        boardPivot.addChild(boardOrient)
        boardOrient.addChild(boardMesh)
        boardMesh.transform().setPosition(Vector3.ZERO)
        runCatching { boardMesh.components.set(GroundShadowComponent(false, true)) }

        // pieces: every prim becomes the visual of one logical piece
        for ((id, prim) in MODEL_NAMES) {
            val piece = model.findEntity(prim)
            if (piece == null) { Log.e(TAG, "prim $prim missing for $id"); return false }
            val side = if (id.startsWith("w_")) Side.WHITE else Side.BLACK
            val kind = kindOf(id)
            piece.removeFromParent()
            piece.transform().setPosition(Vector3.ZERO)
            val pivot = buildPivot(id, side, kind, piece)
            pivots[id] = pivot
            pivotKinds[id] = kind
            if (!prototypes.containsKey(side to kind)) {
                val proto = piece.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true))
                if (proto != null) {
                    proto.enabled = false
                    proto.setName("proto:${side.name}:${kind.name}")
                    prototypes[side to kind] = proto
                }
            }
        }
        // Verify the sign of the conversion with a tall piece: its geometry must point up (+Y).
        val kingPivot = pivots["w_king"]!!
        val kingPiece = kingPivot.findEntity(MODEL_NAMES.getValue("w_king"))
        if (kingPiece != null) {
            var kb = kingPiece.getVisualBounds(kingPivot, true, false)
            Log.i(TAG, "white king bounds rel pivot: min=${kb.min} max=${kb.max}")
            if (kb.max.y < 0.02f || kb.size.y < kb.size.z) {
                upConversion = EulerAngles(pitch = -upConversion.pitch)
                for (o in orients) o.transform().setEulerAngles(upConversion)
                kb = kingPiece.getVisualBounds(kingPivot, true, false)
                Log.i(TAG, "flipped up conversion to pitch=${upConversion.pitch}; king now min=${kb.min} max=${kb.max}")
            }
        }

        if (!loadPrimitives()) return false
        buildSquares()
        buildTrays()
        buildMarkers()
        applyTransform()
        Log.i(TAG, "scene built in ${System.currentTimeMillis() - t0} ms")
        return true
    }

    /**
     * Box / cylinder visuals come from assets/primitives.usdz. The SDK's primitive mesh factories
     * need `com.pico.spatial.foundation.extensions...Option` classes that are missing from the
     * system Spatial runtime on some PICO OS builds (NoClassDefFoundError on device), while USDZ
     * meshes load everywhere.
     */
    private suspend fun loadPrimitives(): Boolean {
        val prims = try {
            Entity.loadSuspend("asset://primitives.usdz")
        } catch (e: Exception) {
            Log.e(TAG, "primitives load failed", e); return false
        }
        primitivesRoot = prims
        boxMesh = prims.findEntity("unit_box")?.components?.get(ModelComponent::class.java)?.mesh
            ?: run { Log.e(TAG, "unit_box missing"); return false }
        cylinderMesh = prims.findEntity("unit_cylinder")?.components?.get(ModelComponent::class.java)?.mesh
            ?: run { Log.e(TAG, "unit_cylinder missing"); return false }
        return true
    }

    private fun boxEntity(name: String, size: Vector3, material: Material): Entity = Entity().apply {
        setName(name)
        components.set(ModelComponent(boxMesh, material))
        transform().setScaleVector(size)
    }

    private fun discEntity(name: String, radius: Float, height: Float, material: Material): Entity = Entity().apply {
        setName(name)
        components.set(ModelComponent(cylinderMesh, material))
        transform().setScaleVector(Vector3(radius * 2f, height, radius * 2f))
    }

    private fun kindOf(id: String): Kind = when {
        id.contains("pawn") -> Kind.PAWN
        id.contains("rook") -> Kind.ROOK
        id.contains("knight") -> Kind.KNIGHT
        id.contains("bishop") -> Kind.BISHOP
        id.contains("queen") -> Kind.QUEEN
        else -> Kind.KING
    }

    private fun buildPivot(id: String, side: Side, kind: Kind, piece: Entity): Entity {
        val pivot = Entity().apply { setName("piece:$id") }
        val orient = Entity().apply { setName("orient:$id") }
        orient.transform().setEulerAngles(upConversion)
        orients.add(orient)
        pivot.addChild(orient)
        orient.addChild(piece)
        piece.enabled = true
        val h = PIECE_HEIGHT.getValue(kind)
        val shape = ShapeResource.createBox(Vector3(0.03f, h, 0.03f)).offsetByTranslation(Vector3(0f, h / 2f, 0f))
        pivot.components.set(CollisionComponent(listOf(shape), PhysicsMaterialResource()))
        pivot.components.set(InteractableComponent())
        runCatching { pivot.components.set(HoverEffectComponent()) }
        runCatching { piece.components.set(GroundShadowComponent(true, false)) }
        boardRoot.addChild(pivot)
        return pivot
    }

    private fun buildSquares() {
        for (i in 0 until 64) {
            val e = Entity().apply { setName("sq:$i") }
            e.transform().setPosition(squareCenter(i) + Vector3(0f, 0.001f, 0f))
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(SQ, 0.003f, SQ))), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            boardRoot.addChild(e)
            squares[i] = e
        }
    }

    private fun buildTrays() {
        // Walnut box with a green felt inlay and cream slot markers: the trays read as a real
        // chess box next to the wooden board instead of a white-model placeholder.
        val walnut = PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(0.30f, 0.19f, 0.11f, 1f)); setRoughness(0.72f); setMetallic(0f)
        }
        val felt = PhysicallyBasedMaterial.create().apply {
            setBaseColor(Color4(0.13f, 0.31f, 0.21f, 1f)); setRoughness(0.98f); setMetallic(0f)
        }
        val cream = UnlitMaterial.create().apply { setBaseColor(Color4(0.88f, 0.82f, 0.66f, 1f)) }
        for (side in Side.values()) {
            val sign = if (side == Side.WHITE) -1f else 1f
            val size = Vector3(2 * TRAY_PITCH + 0.022f, TRAY_TOP, 8 * TRAY_PITCH + 0.022f)
            // collider + interaction live on an unscaled parent; the visuals are scaled children
            val tray = Entity().apply { setName("tray:${side.name}") }
            tray.transform().setPosition(Vector3(sign * (TRAY_X0 + TRAY_PITCH / 2f), TRAY_TOP / 2f, 0f))
            tray.components.set(CollisionComponent(listOf(ShapeResource.createBox(size)), PhysicsMaterialResource()))
            tray.components.set(InteractableComponent())
            boardRoot.addChild(tray)
            val body = boxEntity("trayBody", size, walnut)
            runCatching { body.components.set(GroundShadowComponent(true, true)) }
            tray.addChild(body)
            val inlay = boxEntity("trayInlay", Vector3(size.x - 0.012f, 0.0012f, size.z - 0.012f), felt)
            inlay.transform().setPosition(Vector3(0f, TRAY_TOP / 2f + 0.0004f, 0f))
            tray.addChild(inlay)
            for (slot in 0 until Location.TRAY_CAPACITY) {
                val c = traySlotCenter(side, slot)
                val dot = discEntity("slot", 0.0065f, 0.0008f, cream)
                dot.transform().setPosition(Vector3(c.x, TRAY_TOP + 0.0012f, c.z))
                boardRoot.addChild(dot)
            }
        }
    }

    private fun flatMarker(name: String, w: Float, d: Float, color: Color4): Entity {
        val mat = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply { setBaseColor(color) }
        val e = boxEntity(name, Vector3(w, 0.0015f, d), mat)
        e.enabled = false
        boardRoot.addChild(e)
        return e
    }

    private fun buildMarkers() {
        val ringMat = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply { setBaseColor(Color4(0.23f, 0.38f, 0.89f, 0.65f)) }
        selectionRing = discEntity("marker:selection", 0.021f, 0.0015f, ringMat)
        selectionRing.enabled = false
        boardRoot.addChild(selectionRing)

        targetMarker = flatMarker("marker:target", SQ * 0.92f, SQ * 0.92f, Color4(0.36f, 0.55f, 0.95f, 0.55f))
        captureMarker = flatMarker("marker:capture", SQ * 0.96f, SQ * 0.96f, Color4(0.95f, 0.55f, 0.25f, 0.55f))
        captureInner = flatMarker("marker:capture:inner", SQ * 0.55f, SQ * 0.55f, Color4(0.95f, 0.45f, 0.15f, 0.7f))
        blockedMarker = flatMarker("marker:blocked", SQ * 0.92f, SQ * 0.92f, Color4(0.85f, 0.2f, 0.2f, 0.5f))
    }

    // ------------------------------------------------------------------ transforms

    fun applySettings(scalePercent: Int, yaw: Int, heightCm: Int) {
        userScale = scalePercent / 100f
        yawDegrees = yaw.toFloat()
        heightOffset = heightCm / 100f
        applyTransform()
    }

    fun setOrbit(yaw: Float, pitch: Float, zoom: Float) {
        orbitYaw = yaw; orbitPitch = pitch; orbitZoom = zoom
        applyTransform()
    }

    private fun applyTransform() {
        val s = modelScale * userScale
        boardRoot.transform().apply {
            setPosition(Vector3(0f, 0.002f + heightOffset, 0f))
            setEulerAngles(EulerAngles(yaw = yawDegrees))
            setScaleVector(Vector3(s, s, s))
        }
        val inv = 1f / s
        for (l in labels) {
            l.entity.transform().apply {
                setPosition(l.local)
                setScaleVector(Vector3(inv, inv, inv))
                setEulerAngles(if (l.flat) EulerAngles(pitch = -90f, yaw = l.flatYaw) else EulerAngles(yaw = -yawDegrees))
            }
        }
        sceneRoot.transform().apply {
            setPosition(Vector3(0f, floorY + (if (orbitPitch != 0f) 0.12f else 0f), 0f))
            setEulerAngles(EulerAngles(pitch = orbitPitch, yaw = orbitYaw))
            setScaleVector(Vector3(orbitZoom, orbitZoom, orbitZoom))
        }
    }

    /** Board-local (model units) → sceneRoot-local metres. */
    fun boardToScene(local: Vector3): Vector3 {
        val s = modelScale * userScale
        val rad = Math.toRadians(yawDegrees.toDouble())
        val c = cos(rad).toFloat(); val sn = sin(rad).toFloat()
        val x = local.x * s; val z = local.z * s
        return Vector3(c * x + sn * z, local.y * s + 0.002f + heightOffset, -sn * x + c * z)
    }

    /** sceneRoot-local metre delta → board-local model-unit delta. */
    fun sceneDeltaToBoard(d: Vector3): Vector3 {
        val s = modelScale * userScale
        val rad = Math.toRadians(-yawDegrees.toDouble())
        val c = cos(rad).toFloat(); val sn = sin(rad).toFloat()
        val x = d.x / s; val z = d.z / s
        return Vector3(c * x + sn * z, d.y / s, -sn * x + c * z)
    }

    /**
     * Attaches a 2D attachment-panel entity to the board in board-local coordinates. Labels keep
     * their real-world size (counter-scaled) and either face the user or lie flat on the board.
     */
    fun addLabel(entity: Entity, boardLocal: Vector3, flat: Boolean = false, flatYaw: Float = 0f) {
        labels.removeAll { it.entity === entity }
        boardRoot.addChild(entity)
        labels.add(Label(entity, boardLocal, flat, flatYaw))
        applyTransform()
    }

    /** Attaches a label at a fixed position in sceneRoot space (metres, floor at y = 0). */
    fun addSceneLabel(entity: Entity, position: Vector3, pitch: Float = 0f) {
        sceneRoot.addChild(entity)
        entity.transform().setPosition(position)
        entity.transform().setEulerAngles(EulerAngles(pitch = pitch))
    }

    /** Debug: board-local position of every piece pivot. */
    fun dumpPieces(): String = pivots.entries.sortedBy { it.key }.joinToString("\n") { (id, e) ->
        val p = e.transform().position
        "%s -> (%.4f, %.4f, %.4f) enabled=%s".format(id, p.x, p.y, p.z, e.enabled)
    }

    // ------------------------------------------------------------------ pieces

    fun pivotOf(id: String): Entity? = pivots[id]

    /** Ensures the pivot for [id] shows [kind]; returns null when a promoted model cannot be created. */
    fun ensurePieceEntity(id: String, side: Side, kind: Kind): Entity? {
        val current = pivots[id]
        if (current != null && pivotKinds[id] == kind) return current
        val spare = sparePivots.remove(id to kind)
        val next = spare ?: run {
            val proto = prototypes[side to kind] ?: return null
            val clone = proto.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true)) ?: return null
            clone.enabled = true
            clone.setName("${proto.getName()}:clone")
            buildPivot(id, side, kind, clone)
        }
        if (current != null) {
            current.enabled = false
            sparePivots[id to pivotKinds.getValue(id)] = current
            next.transform().setPosition(current.transform().position)
        }
        next.enabled = true
        pivots[id] = next
        pivotKinds[id] = kind
        return next
    }

    fun syncAll(snapshot: Snapshot) {
        for (p in snapshot.pieces) {
            val pivot = ensurePieceEntity(p.id, p.side, p.kind) ?: continue
            val pos = locationCenter(p.location)
            piecePositions[p.id] = pos
            pivot.transform().setPosition(pos)
        }
    }

    /** Animates [change] then leaves every piece exactly where [after] says (PRD chapter 6 timings). */
    fun applyChange(after: Snapshot, change: Change, reduceMotion: Boolean, onDone: () -> Unit) {
        // make sure kinds (promotion / undo of promotion) are right before moving anything
        for (p in after.pieces) ensurePieceEntity(p.id, p.side, p.kind)
        val moves: List<Change.Move> = when (change) {
            is Change.Move -> listOf(change)
            is Change.Capture -> emptyList()
            is Change.Bulk -> change.moves
            is Change.Promote, Change.None -> emptyList()
        }
        fun finish() { syncAll(after); onDone() }
        if (reduceMotion) { finish(); flash(targetMarker, change); return }
        when (change) {
            is Change.Capture -> {
                val victim = pivots[change.capturedId]
                val attacker = pivots[change.attackerId]
                val victimTo = after.byId[change.capturedId]?.location?.let(::locationCenter)
                val attackerTo = squareCenter(change.to)
                if (victim == null || attacker == null || victimTo == null) { finish(); return }
                tween(victim, victimTo, CAPTURE_STORE_MS, LIFT_ANIM) {
                    tween(attacker, attackerTo, MOVE_MS, LIFT_ANIM) { finish() }
                }
            }
            else -> {
                if (moves.isEmpty()) { finish(); return }
                val animated = moves.mapNotNull { m -> pivots[m.id]?.let { it to m } }
                var remaining = animated.size
                if (remaining == 0) { finish(); return }
                for ((pivot, m) in animated) {
                    tween(pivot, locationCenter(m.to), MOVE_MS, LIFT_ANIM) { if (--remaining == 0) finish() }
                }
            }
        }
    }

    private fun flash(marker: Entity, change: Change) {
        val square = when (change) {
            is Change.Move -> (change.to as? Location.Board)?.square
            is Change.Capture -> change.to
            else -> null
        } ?: return
        marker.transform().setPosition(squareCenter(square) + Vector3(0f, 0.0012f, 0f))
        marker.enabled = true
        tween(marker, marker.transform().position, 300f, 0f) { marker.enabled = false }
    }

    private fun tween(entity: Entity, to: Vector3, ms: Float, lift: Float, onDone: (() -> Unit)?) {
        tweens.removeAll { it.entity === entity }
        tweens.add(Tween(entity, entity.transform().position, to, ms, lift, onDone))
    }

    fun tick(nowNs: Long) {
        if (tweens.isEmpty()) return
        val done = ArrayList<Tween>()
        for (tw in tweens.toList()) {
            if (tw.startNs < 0) tw.startNs = nowNs
            val t = if (tw.durationMs <= 0f) 1f else ((nowNs - tw.startNs) / 1_000_000f / tw.durationMs).coerceIn(0f, 1f)
            val e = 1f - (1f - t) * (1f - t) * (1f - t)
            val p = Vector3.lerp(tw.from, tw.to, e)
            val lifted = Vector3(p.x, p.y + tw.lift * sin(PI.toFloat() * t), p.z)
            tw.entity.transform().setPosition(if (t >= 1f) tw.to else lifted)
            if (t >= 1f) done.add(tw)
        }
        tweens.removeAll(done.toSet())
        for (tw in done) tw.onDone?.invoke()
    }

    // ------------------------------------------------------------------ selection & markers

    fun setSelected(id: String?, snapshot: Snapshot) {
        // restore every committed position first (drops any lift)
        for ((pid, pos) in piecePositions) pivots[pid]?.takeIf { tweens.none { t -> t.entity === it } }?.transform()?.setPosition(pos)
        if (id == null) { selectionRing.enabled = false; return }
        val pivot = pivots[id] ?: return
        val base = piecePositions[id] ?: return
        pivot.transform().setPosition(base + Vector3(0f, LIFT_SELECTED, 0f))
        selectionRing.transform().setPosition(base + Vector3(0f, 0.0012f, 0f))
        selectionRing.enabled = true
    }

    fun showTarget(square: Int?, capture: Boolean, blocked: Boolean = false) {
        targetMarker.enabled = false; captureMarker.enabled = false; captureInner.enabled = false; blockedMarker.enabled = false
        if (square == null) return
        val m = if (blocked) blockedMarker else if (capture) captureMarker else targetMarker
        m.transform().setPosition(squareCenter(square) + Vector3(0f, 0.0012f, 0f))
        m.enabled = true
        if (capture && !blocked) {
            captureInner.transform().setPosition(squareCenter(square) + Vector3(0f, 0.0022f, 0f))
            captureInner.enabled = true
        }
    }

    // ------------------------------------------------------------------ grab & snap

    private var grabbedId: String? = null
    private var grabOrigin: Vector3? = null

    fun beginGrab(id: String, snapshot: Snapshot) {
        val pivot = pivots[id] ?: return
        grabbedId = id
        grabOrigin = piecePositions[id]
        tweens.removeAll { it.entity === pivot }
        setSelected(id, snapshot)
        pivot.transform().setPosition(pivot.transform().position + Vector3(0f, 0.012f, 0f))
    }

    /** Moves the grabbed piece by a scene-space delta (grab offset is preserved implicitly). */
    fun moveGrabbed(sceneDelta: Vector3, snapshot: Snapshot): DropCandidate {
        val id = grabbedId ?: return DropCandidate.None
        val pivot = pivots[id] ?: return DropCandidate.None
        val d = sceneDeltaToBoard(sceneDelta)
        val p = pivot.transform().position + d
        val clampedY = p.y.coerceIn(BOARD_TOP + 0.004f, 0.25f)
        pivot.transform().setPosition(Vector3(p.x, clampedY, p.z))
        return candidateFor(Vector3(p.x, 0f, p.z), snapshot, id)
    }

    fun candidateFor(localXZ: Vector3, snapshot: Snapshot, id: String): DropCandidate {
        val limit = 4f * SQ + 0.3f * SQ
        if (abs(localXZ.x) <= limit && abs(localXZ.z) <= limit) {
            val f = (localXZ.x / SQ + 3.5f).roundToInt().coerceIn(0, 7)
            val r = (-localXZ.z / SQ + 3.5f).roundToInt().coerceIn(0, 7)
            return DropCandidate.Square(Square.index(f, r))
        }
        val side = snapshot.byId[id]?.side ?: return DropCandidate.None
        val sign = if (side == Side.WHITE) -1f else 1f
        val cx = sign * (TRAY_X0 + TRAY_PITCH / 2f)
        if (abs(localXZ.x - cx) <= TRAY_PITCH * 1.6f && abs(localXZ.z) <= 4.4f * TRAY_PITCH) return DropCandidate.Tray(side)
        return DropCandidate.None
    }

    fun currentGrabCandidate(snapshot: Snapshot): DropCandidate {
        val id = grabbedId ?: return DropCandidate.None
        val p = pivots[id]?.transform()?.position ?: return DropCandidate.None
        return candidateFor(Vector3(p.x, 0f, p.z), snapshot, id)
    }

    /** Ends the grab: snap to [target] (or back to the origin) in ~120 ms and restore uprightness. */
    fun endGrab(target: Vector3?, onDone: () -> Unit) {
        val id = grabbedId ?: return
        val pivot = pivots[id]
        val origin = grabOrigin
        grabbedId = null; grabOrigin = null
        if (pivot == null) { onDone(); return }
        val dest = target ?: origin ?: pivot.transform().position
        tween(pivot, dest, 120f, 0f) { onDone() }
    }

    fun cancelGrab() {
        val id = grabbedId ?: return
        val pivot = pivots[id]; val origin = grabOrigin
        grabbedId = null; grabOrigin = null
        if (pivot != null && origin != null) pivot.transform().setPosition(origin)
    }

    fun destroy() {
        runCatching { content.removeEntity(sceneRoot) }
        runCatching { sceneRoot.destroy(true) }
        runCatching { modelRoot?.destroy(true) }
        runCatching { primitivesRoot?.destroy(true) }
    }
}

fun Entity.transform(): TransformComponent = components[TransformComponent::class.java]!!
