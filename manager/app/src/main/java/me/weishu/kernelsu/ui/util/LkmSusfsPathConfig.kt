package me.weishu.kernelsu.ui.util

import org.json.JSONArray
import org.json.JSONObject

internal const val LKM_SUSFS_PATH_CONFIG_MAX_BYTES = 128 * 1024
internal const val LKM_SUSFS_PATH_CONFIG_MAX_PATHS = 256
private const val LKM_SUSFS_PATH_CONFIG_KIND = "stersu.lkm-susfs-paths"
private const val LKM_SUSFS_PATH_CONFIG_VERSION = 1

internal fun normalizeLkmSusfsPath(value: String): String {
    val path = value.trimEnd('/')
    require(path.startsWith('/') && path != "/" && path.toByteArray(Charsets.UTF_8).size < 255 &&
        !path.endsWith(' ') && path.none { it.code < 32 || it.code == 127 } &&
        path.split('/').none { it == "." || it == ".." }) {
        "Invalid LKM SUSFS path: $value"
    }
    return path
}

internal fun encodeLkmSusfsPathConfig(paths: List<String>): String {
    require(paths.size <= LKM_SUSFS_PATH_CONFIG_MAX_PATHS) { "Too many LKM SUSFS paths" }
    val normalized = paths.map(::normalizeLkmSusfsPath)
    require(normalized.size == normalized.distinct().size) { "Duplicate LKM SUSFS paths" }
    return JSONObject()
        .put("kind", LKM_SUSFS_PATH_CONFIG_KIND)
        .put("schemaVersion", LKM_SUSFS_PATH_CONFIG_VERSION)
        .put("paths", JSONArray(normalized))
        .toString(2)
}

internal fun decodeLkmSusfsPathConfig(content: String): List<String> {
    require(content.toByteArray(Charsets.UTF_8).size <= LKM_SUSFS_PATH_CONFIG_MAX_BYTES) {
        "LKM SUSFS path config is too large"
    }
    val root = JSONObject(content)
    require(root.opt("kind") == LKM_SUSFS_PATH_CONFIG_KIND &&
        root.opt("schemaVersion") == LKM_SUSFS_PATH_CONFIG_VERSION) {
        "Unsupported LKM SUSFS path config"
    }
    val array = root.optJSONArray("paths") ?: error("Missing LKM SUSFS paths")
    require(array.length() <= LKM_SUSFS_PATH_CONFIG_MAX_PATHS) { "Too many LKM SUSFS paths" }
    return (0 until array.length()).map { index ->
        val value = array.get(index)
        require(value is String) { "LKM SUSFS path must be text" }
        normalizeLkmSusfsPath(value)
    }
}

internal fun planLkmSusfsPathImport(imported: List<String>, current: List<String>): List<String> {
    val existing = current.map(::normalizeLkmSusfsPath).toSet()
    val additions = imported.map(::normalizeLkmSusfsPath).distinct().filterNot(existing::contains)
    require(existing.size + additions.size <= LKM_SUSFS_PATH_CONFIG_MAX_PATHS) {
        "LKM SUSFS path limit exceeded"
    }
    return additions
}
