package snd.komelia.readingorder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import snd.komelia.links.SeriesRelationEdge
import snd.komelia.links.SeriesRelationType
import snd.komga.client.series.KomgaSeriesId

/** Where a series sits in the reading order, relative to the original one. */
enum class ReadingOrderKind {
    /** The series to start with. Exactly one per graph. */
    ORIGINAL,

    /** Continues the story — the spine, drawn with a solid arrow. */
    SEQUEL,

    /**
     * Happens earlier but is read AFTER the original. That is the whole reason
     * this feature exists: "prequel" was being read as "read this first", and
     * Fairy Tail Zero is not where anyone should start Fairy Tail.
     */
    PREQUEL,

    /** Side story. Read whenever. */
    SPIN_OFF,

    /**
     * Same world, another format (a light novel next to the manga). Kept in the
     * graph — unlike editions, it is something else to read.
     */
    RELATED,

    /**
     * The series this one is a spin-off OF. Only ever appears when the graph is
     * drawn from a spin-off — which happens while no original is designated.
     * Following it is what keeps such a graph from being a single dead-end box.
     */
    MAIN_STORY,
}

/**
 * One box in the graph. An edition group (a series plus its other-language and
 * colour editions) is ONE node: showing four boxes for the same work would be
 * noise, not a reading order.
 */
@Serializable
data class ReadingOrderNode(
    @SerialName("id") val seriesId: String,
    @SerialName("t") val title: String,
    @SerialName("k") val kind: ReadingOrderKind,
    /** Distance from the original, for layout. 0 = the original itself. */
    @SerialName("d") val depth: Int,
    /** Series folded into this box as editions; 0 when there are none. */
    @SerialName("e") val editionCount: Int = 0,
    /** Node this one hangs from — the original, or a sequel further along. */
    @SerialName("p") val parentSeriesId: String? = null,
)

/** The computed graph, as displayed and as cached. */
@Serializable
data class ReadingOrderGraph(
    @SerialName("root") val originalSeriesId: String,
    @SerialName("n") val nodes: List<ReadingOrderNode> = emptyList(),
    /**
     * True when the franchise was bigger than [MAX_NODES] or deeper than
     * [MAX_DEPTH] and had to be cut. The UI says so instead of pretending the
     * picture is complete.
     */
    @SerialName("cut") val truncated: Boolean = false,
) {
    val isWorthShowing: Boolean get() = nodes.size > 1
}

/** Past these the picture stops being readable — the lists below it are better. */
const val MAX_NODES = 8
const val MAX_DEPTH = 3

/**
 * Relations that are about EDITIONS, not about reading order. Two series linked
 * this way are the same work: they collapse into a single node.
 */
private val EDITION_TYPES = setOf(SeriesRelationType.LANGUAGE, SeriesRelationType.COLORED)

/**
 * Builds the reading order around [originalSeriesId], from the whole local link
 * tables (two cheap queries, no per-series round trip).
 *
 * [versionGroups] is seriesId -> group id, the symmetric "other versions" set;
 * those are editions too and fold into their series' node.
 *
 * The walk is deliberately breadth-first from the original: depth is what the
 * layout uses, and taking the shortest path to each series keeps a spin-off of
 * a sequel from being drawn as a spin-off of the original.
 */
fun buildReadingOrder(
    originalSeriesId: KomgaSeriesId,
    relations: List<SeriesRelationEdge>,
    versionGroups: Map<KomgaSeriesId, String>,
    titles: Map<String, String>,
): ReadingOrderGraph {
    val rootId = originalSeriesId.value

    // 1. Fold editions together. Same-work links (versions group, language,
    //    colour) all point at one representative id.
    val representative = editionRepresentatives(rootId, relations, versionGroups)
    val editionCounts = representative.entries
        .groupingBy { it.value }
        .eachCount()
        .mapValues { (_, count) -> count - 1 }

    // 2. Story edges only, rewritten between representatives, self-loops dropped
    //    (they are what an edition link becomes once folded).
    val storyEdges = relations
        .filterNot { it.type in EDITION_TYPES }
        .map { edge ->
            Triple(
                representative[edge.from.value] ?: edge.from.value,
                representative[edge.to.value] ?: edge.to.value,
                edge.type,
            )
        }
        .filterNot { (from, to, _) -> from == to }
        .distinct()
    val outgoing = storyEdges.groupBy({ it.first }, { it.second to it.third })

    // 3. Breadth-first from the original.
    val nodes = mutableListOf(
        ReadingOrderNode(
            seriesId = rootId,
            title = titles[rootId] ?: rootId,
            kind = ReadingOrderKind.ORIGINAL,
            depth = 0,
            editionCount = editionCounts[rootId] ?: 0,
        )
    )
    val visited = mutableSetOf(rootId)
    var frontier = listOf(rootId)
    var depth = 1
    var truncated = false

    while (frontier.isNotEmpty() && depth <= MAX_DEPTH) {
        val next = mutableListOf<String>()
        for (parent in frontier) {
            val neighbours = outgoing[parent].orEmpty()
                // Stable order: the spine first, then the optional branches, so
                // two runs on the same data draw the same picture.
                .sortedWith(compareBy({ kindOrder(it.second) }, { titles[it.first] ?: it.first }))
            for ((childId, type) in neighbours) {
                if (childId in visited) continue
                val kind = type.toKind() ?: continue
                if (nodes.size >= MAX_NODES) {
                    truncated = true
                    break
                }
                visited += childId
                next += childId
                nodes += ReadingOrderNode(
                    seriesId = childId,
                    title = titles[childId] ?: childId,
                    kind = kind,
                    depth = depth,
                    editionCount = editionCounts[childId] ?: 0,
                    parentSeriesId = parent,
                )
            }
            if (nodes.size >= MAX_NODES) break
        }
        if (nodes.size >= MAX_NODES && next.isNotEmpty()) truncated = true
        frontier = next
        depth++
    }
    if (frontier.isNotEmpty()) truncated = true

    return ReadingOrderGraph(originalSeriesId = rootId, nodes = nodes, truncated = truncated)
}

/**
 * seriesId -> the id that stands for its edition group.
 *
 * The original always represents its own group, so the box the user designated
 * is the one that gets drawn, not whichever language happened to sort first.
 */
private fun editionRepresentatives(
    rootId: String,
    relations: List<SeriesRelationEdge>,
    versionGroups: Map<KomgaSeriesId, String>,
): Map<String, String> {
    val neighbours = mutableMapOf<String, MutableSet<String>>()
    fun connect(a: String, b: String) {
        neighbours.getOrPut(a) { mutableSetOf() } += b
        neighbours.getOrPut(b) { mutableSetOf() } += a
    }
    relations.filter { it.type in EDITION_TYPES }.forEach { connect(it.from.value, it.to.value) }
    versionGroups.entries.groupBy({ it.value }, { it.key.value })
        .values
        .forEach { members -> members.forEach { member -> members.forEach { other -> if (member != other) connect(member, other) } } }

    val representative = mutableMapOf<String, String>()
    for (start in neighbours.keys) {
        if (start in representative) continue
        val group = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!group.add(current)) continue
            neighbours[current].orEmpty().forEach { if (it !in group) queue += it }
        }
        val head = if (rootId in group) rootId else group.min()
        group.forEach { representative[it] = head }
    }
    return representative
}

/** Editions never reach here; anything else is something to read. */
private fun SeriesRelationType.toKind(): ReadingOrderKind? = when (this) {
    SeriesRelationType.SEQUEL -> ReadingOrderKind.SEQUEL
    SeriesRelationType.PREQUEL -> ReadingOrderKind.PREQUEL
    SeriesRelationType.SPIN_OFF -> ReadingOrderKind.SPIN_OFF
    SeriesRelationType.RELATED -> ReadingOrderKind.RELATED
    SeriesRelationType.MAIN_STORY -> ReadingOrderKind.MAIN_STORY
    SeriesRelationType.LANGUAGE, SeriesRelationType.COLORED -> null
}

private fun kindOrder(type: SeriesRelationType): Int = when (type) {
    SeriesRelationType.SEQUEL -> 0
    SeriesRelationType.MAIN_STORY -> 1
    SeriesRelationType.PREQUEL -> 2
    SeriesRelationType.SPIN_OFF -> 3
    SeriesRelationType.RELATED -> 4
    else -> 5
}
