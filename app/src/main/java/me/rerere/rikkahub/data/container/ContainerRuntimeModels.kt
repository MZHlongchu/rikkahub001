package me.rerere.rikkahub.data.container

data class ContainerInventorySnapshot(
    val apkPackages: List<String> = emptyList(),
    val pythonPackages: List<String> = emptyList(),
    val containerSizeBytes: Long = 0L,
    val layoutVersion: Int? = null,
)

data class ContainerLayoutStatus(
    val version: Int? = null,
    val compatible: Boolean = false,
    val needsRebuild: Boolean = false,
    val reason: String? = null,
    val hasLegacyLayout: Boolean = false,
    val hasCurrentLayout: Boolean = false,
)
