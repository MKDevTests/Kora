package snd.komelia.ui.library

enum class CollectionReadListSortOrder(val label: String) {
    NAME_ASC("Name A→Z"),
    NAME_DESC("Name Z→A"),
    CREATED_DESC("Newest first"),
    CREATED_ASC("Oldest first"),
    COUNT_DESC("Most items"),
    COUNT_ASC("Fewest items"),
}
