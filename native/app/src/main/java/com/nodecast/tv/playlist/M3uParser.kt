package com.nodecast.tv.playlist

object M3uParser {

    private const val MAX_CHANNELS = 5000

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var name = ""
        var group = ""
        var logo = ""
        var pendingInfo = false

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    group = attrs["group-title"].orEmpty()
                    logo = attrs["tvg-logo"].orEmpty()
                    name = line.substringAfterLast(',', "").trim()
                    if (name.isEmpty()) name = attrs["tvg-name"].orEmpty()
                    pendingInfo = true
                }
                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    group = line.substringAfter(':', "").trim()
                }
                line.isEmpty() || line.startsWith("#") -> Unit
                pendingInfo -> {
                    channels.add(Channel(name.ifEmpty { line }, line, group, logo))
                    if (channels.size >= MAX_CHANNELS) return channels
                    name = ""
                    group = ""
                    logo = ""
                    pendingInfo = false
                }
                else -> {
                    // Bare URL without #EXTINF — still a playable entry.
                    channels.add(Channel(line, line, "", ""))
                    if (channels.size >= MAX_CHANNELS) return channels
                }
            }
        }
        return channels
    }
}
