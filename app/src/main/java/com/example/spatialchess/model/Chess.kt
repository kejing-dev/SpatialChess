package com.example.spatialchess.model

import org.json.JSONArray
import org.json.JSONObject

enum class Side(val key: String) { WHITE("side.white"), BLACK("side.black") }

enum class Kind(val key: String) {
    PAWN("piece.pawn"), ROOK("piece.rook"), KNIGHT("piece.knight"),
    BISHOP("piece.bishop"), QUEEN("piece.queen"), KING("piece.king")
}

/** 0..63, rank-major: index = rank * 8 + file; a1 = 0, h1 = 7, a8 = 56. */
object Square {
    fun index(file: Int, rank: Int) = rank * 8 + file
    fun file(index: Int) = index % 8
    fun rank(index: Int) = index / 8
    fun name(index: Int): String = "${'a' + file(index)}${rank(index) + 1}"
    fun parse(name: String): Int? {
        val s = name.trim().lowercase()
        if (s.length != 2) return null
        val f = s[0] - 'a'
        val r = s[1] - '1'
        return if (f in 0..7 && r in 0..7) index(f, r) else null
    }
    /** a1 is a dark square, h1 is light. */
    fun isLight(index: Int) = (file(index) + rank(index)) % 2 == 1
}

sealed class Location {
    data class Board(val square: Int) : Location()
    data class Tray(val side: Side, val slot: Int) : Location()

    fun encode(): String = when (this) {
        is Board -> "b:$square"
        is Tray -> "t:${side.name}:$slot"
    }

    companion object {
        const val TRAY_CAPACITY = 16
        fun decode(s: String): Location? {
            val p = s.split(":")
            return when {
                p.size == 2 && p[0] == "b" -> p[1].toIntOrNull()?.takeIf { it in 0..63 }?.let { Board(it) }
                p.size == 3 && p[0] == "t" -> {
                    val side = runCatching { Side.valueOf(p[1]) }.getOrNull() ?: return null
                    p[2].toIntOrNull()?.takeIf { it in 0 until TRAY_CAPACITY }?.let { Tray(side, it) }
                }
                else -> null
            }
        }
    }
}

/**
 * Logical piece. [id] is the stable identity (survives promotion, capture, undo); [kind] changes on
 * promotion; the rendering entity is resolved by the scene from (id, side, kind).
 */
data class PieceState(val id: String, val side: Side, val kind: Kind, val location: Location)

/** Immutable board snapshot: exactly 32 pieces, every location unique. */
data class Snapshot(val pieces: List<PieceState>) {
    val byId: Map<String, PieceState> by lazy { pieces.associateBy { it.id } }
    val bySquare: Map<Int, PieceState> by lazy {
        pieces.mapNotNull { p -> (p.location as? Location.Board)?.let { it.square to p } }.toMap()
    }

    fun at(square: Int): PieceState? = bySquare[square]
    fun trayPieces(side: Side): List<PieceState> =
        pieces.filter { it.location is Location.Tray && (it.location as Location.Tray).side == side }
    fun trayCount(side: Side) = trayPieces(side).size
    fun freeTraySlot(side: Side): Int? {
        val used = trayPieces(side).map { (it.location as Location.Tray).slot }.toSet()
        return (0 until Location.TRAY_CAPACITY).firstOrNull { it !in used }
    }
    fun boardCount() = pieces.count { it.location is Location.Board }

    fun with(id: String, transform: (PieceState) -> PieceState): Snapshot =
        Snapshot(pieces.map { if (it.id == id) transform(it) else it })

    fun isValid(): Boolean {
        if (pieces.size != 32) return false
        if (pieces.map { it.id }.toSet().size != 32) return false
        val locs = pieces.map { it.location.encode() }
        return locs.toSet().size == 32
    }

    fun toJson(): JSONArray {
        val arr = JSONArray()
        for (p in pieces) {
            arr.put(JSONObject().apply {
                put("id", p.id); put("side", p.side.name); put("kind", p.kind.name)
                put("loc", p.location.encode())
            })
        }
        return arr
    }

    companion object {
        fun fromJson(arr: JSONArray): Snapshot? {
            val list = ArrayList<PieceState>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: return null
                val id = o.optString("id").ifEmpty { return null }
                val side = runCatching { Side.valueOf(o.optString("side")) }.getOrNull() ?: return null
                val kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
                val loc = Location.decode(o.optString("loc")) ?: return null
                list.add(PieceState(id, side, kind, loc))
            }
            val snap = Snapshot(list)
            return if (snap.isValid()) snap else null
        }

        /** Standard opening: white on ranks 1-2, black on 7-8, white queen d1, white king e1. */
        fun standard(): Snapshot {
            val list = ArrayList<PieceState>()
            val back = listOf(Kind.ROOK, Kind.KNIGHT, Kind.BISHOP, Kind.QUEEN, Kind.KING, Kind.BISHOP, Kind.KNIGHT, Kind.ROOK)
            for (side in Side.values()) {
                val prefix = if (side == Side.WHITE) "w" else "b"
                val backRank = if (side == Side.WHITE) 0 else 7
                val pawnRank = if (side == Side.WHITE) 1 else 6
                for (f in 0 until 8) {
                    val fileChar = 'a' + f
                    val kind = back[f]
                    val id = when (kind) {
                        Kind.QUEEN -> "${prefix}_queen"
                        Kind.KING -> "${prefix}_king"
                        else -> "${prefix}_${kind.name.lowercase()}_$fileChar"
                    }
                    list.add(PieceState(id, side, kind, Location.Board(Square.index(f, backRank))))
                    list.add(PieceState("${prefix}_pawn_$fileChar", side, Kind.PAWN, Location.Board(Square.index(f, pawnRank))))
                }
            }
            return Snapshot(list)
        }

        /** Every piece in its tray, nothing on the board; models are never deleted (PRD F07). */
        fun cleared(from: Snapshot): Snapshot {
            val counters = mutableMapOf(Side.WHITE to 0, Side.BLACK to 0)
            val ordered = from.pieces.sortedWith(compareBy({ it.side }, { it.kind.ordinal }, { it.id }))
            return Snapshot(ordered.map { p ->
                val slot = counters.getValue(p.side)
                counters[p.side] = slot + 1
                p.copy(location = Location.Tray(p.side, slot))
            })
        }
    }
}

/** What changed between two snapshots, so the scene can animate it in PRD order. */
sealed class Change {
    data class Move(val id: String, val from: Location, val to: Location) : Change()
    data class Capture(val attackerId: String, val from: Int, val to: Int, val capturedId: String, val traySlot: Int) : Change()
    data class Promote(val id: String, val square: Int, val fromKind: Kind, val toKind: Kind) : Change()
    data class Bulk(val moves: List<Move>) : Change()
    object None : Change()
}

sealed class CommandResult {
    data class Ok(val change: Change) : CommandResult()
    data class Rejected(val reasonKey: String) : CommandResult()
}

/**
 * Data is the single source of truth (PRD chapter 9). Every command validates against the current
 * revision, produces a full before/after snapshot and appends exactly one history entry.
 */
class BoardState(initial: Snapshot = Snapshot.standard()) {
    var current: Snapshot = initial
        private set
    var revision: Long = 0
        private set
    private val history = ArrayList<Snapshot>().apply { add(initial) }
    private var cursor = 0

    val canUndo: Boolean get() = cursor > 0
    val canRedo: Boolean get() = cursor < history.size - 1
    val historySize: Int get() = history.size

    private fun commit(next: Snapshot) {
        require(next.isValid()) { "invalid snapshot" }
        while (history.size - 1 > cursor) history.removeAt(history.size - 1)
        history.add(next)
        if (history.size > MAX_HISTORY) history.removeAt(0) else cursor++
        cursor = history.size - 1
        current = next
        revision++
    }

    fun move(id: String, to: Int): CommandResult {
        val p = current.byId[id] ?: return CommandResult.Rejected("runtime.target.invalid")
        val occupant = current.at(to)
        if (occupant != null) return CommandResult.Rejected("runtime.target.blocked")
        val from = p.location
        commit(current.with(id) { it.copy(location = Location.Board(to)) })
        return CommandResult.Ok(Change.Move(id, from, Location.Board(to)))
    }

    /** Captured piece goes to its own side's tray first, then the attacker moves (PRD chapter 6). */
    fun capture(attackerId: String, to: Int): CommandResult {
        val attacker = current.byId[attackerId] ?: return CommandResult.Rejected("runtime.target.invalid")
        val victim = current.at(to) ?: return CommandResult.Rejected("runtime.target.invalid")
        if (victim.side == attacker.side) return CommandResult.Rejected("runtime.target.blocked")
        val from = (attacker.location as? Location.Board)?.square ?: return CommandResult.Rejected("runtime.target.invalid")
        val slot = current.freeTraySlot(victim.side) ?: return CommandResult.Rejected("runtime.tray.full")
        val next = current
            .with(victim.id) { it.copy(location = Location.Tray(victim.side, slot)) }
            .with(attackerId) { it.copy(location = Location.Board(to)) }
        commit(next)
        return CommandResult.Ok(Change.Capture(attackerId, from, to, victim.id, slot))
    }

    fun store(id: String): CommandResult {
        val p = current.byId[id] ?: return CommandResult.Rejected("runtime.target.invalid")
        if (p.location is Location.Tray) return CommandResult.Rejected("runtime.target.invalid")
        val slot = current.freeTraySlot(p.side) ?: return CommandResult.Rejected("runtime.tray.full")
        val from = p.location
        commit(current.with(id) { it.copy(location = Location.Tray(p.side, slot)) })
        return CommandResult.Ok(Change.Move(id, from, Location.Tray(p.side, slot)))
    }

    fun restore(id: String, to: Int): CommandResult = move(id, to)

    fun promote(id: String, kind: Kind): CommandResult {
        val p = current.byId[id] ?: return CommandResult.Rejected("runtime.target.invalid")
        if (kind == Kind.KING || kind == Kind.PAWN) return CommandResult.Rejected("runtime.target.invalid")
        val sq = (p.location as? Location.Board)?.square ?: return CommandResult.Rejected("runtime.target.invalid")
        commit(current.with(id) { it.copy(kind = kind) })
        return CommandResult.Ok(Change.Promote(id, sq, p.kind, kind))
    }

    fun reset(): CommandResult {
        val before = current
        val next = Snapshot.standard().let { std ->
            // keep the existing ids/kinds identity where possible: standard() uses the same ids
            std
        }
        commit(next)
        return CommandResult.Ok(Change.Bulk(diff(before, next)))
    }

    fun clear(): CommandResult {
        val before = current
        val next = Snapshot.cleared(before)
        commit(next)
        return CommandResult.Ok(Change.Bulk(diff(before, next)))
    }

    fun undo(): Change? {
        if (!canUndo) return null
        val before = current
        cursor--
        current = history[cursor]
        revision++
        return Change.Bulk(diff(before, current))
    }

    fun redo(): Change? {
        if (!canRedo) return null
        val before = current
        cursor++
        current = history[cursor]
        revision++
        return Change.Bulk(diff(before, current))
    }

    fun isPromotionSquare(id: String): Boolean {
        val p = current.byId[id] ?: return false
        if (p.kind != Kind.PAWN) return false
        val sq = (p.location as? Location.Board)?.square ?: return false
        val rank = Square.rank(sq)
        return (p.side == Side.WHITE && rank == 7) || (p.side == Side.BLACK && rank == 0)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("revision", revision)
        put("cursor", cursor)
        put("history", JSONArray().apply { for (h in history) put(h.toJson()) })
    }

    companion object {
        const val MAX_HISTORY = 120

        fun diff(a: Snapshot, b: Snapshot): List<Change.Move> =
            b.pieces.mapNotNull { p ->
                val old = a.byId[p.id] ?: return@mapNotNull null
                if (old.location != p.location) Change.Move(p.id, old.location, p.location) else null
            }

        fun fromJson(o: JSONObject): BoardState? {
            val hist = o.optJSONArray("history") ?: return null
            val snaps = ArrayList<Snapshot>()
            for (i in 0 until hist.length()) {
                val arr = hist.optJSONArray(i) ?: return null
                snaps.add(Snapshot.fromJson(arr) ?: return null)
            }
            if (snaps.isEmpty()) return null
            val cursor = o.optInt("cursor", snaps.size - 1).coerceIn(0, snaps.size - 1)
            val state = BoardState(snaps[0])
            state.history.clear(); state.history.addAll(snaps)
            state.cursor = cursor
            state.current = snaps[cursor]
            state.revision = o.optLong("revision", 0)
            return state
        }
    }
}
