package io.niko.fmt

/** The ports other Yggdrasil nodes may reach on this device: single ports and ranges ("22, 8000-8080"). */
data class MeshPorts(val ports: List<Int>, val ranges: List<String>) {
    companion object {
        /** null when the text names no valid port: the mesh then reaches every port but Niko's own. */
        fun parse(text: String?): MeshPorts? {
            val ports = mutableListOf<Int>()
            val ranges = mutableListOf<String>()
            for (part in text.orEmpty().split(',', ' ', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }) {
                val range = part.split('-', ':')
                when (range.size) {
                    1 -> range[0].toIntOrNull()?.takeIf { it in 1..65535 }?.let(ports::add)
                    2 -> {
                        val from = range[0].toIntOrNull() ?: continue
                        val to = range[1].toIntOrNull() ?: continue
                        if (from in 1..to && to <= 65535) ranges += "$from:$to"
                    }
                }
            }
            return if (ports.isEmpty() && ranges.isEmpty()) null else MeshPorts(ports.distinct(), ranges.distinct())
        }
    }
}
