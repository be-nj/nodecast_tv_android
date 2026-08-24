package com.nodecast.tv.playlist

import org.json.JSONArray
import org.json.JSONObject

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("url", url)
        .put("group", group)
        .put("logo", logo)

    companion object {
        fun fromJson(obj: JSONObject): Channel = Channel(
            name = obj.optString("name"),
            url = obj.optString("url"),
            group = obj.optString("group"),
            logo = obj.optString("logo"),
        )

        fun listToJson(channels: List<Channel>): JSONArray {
            val arr = JSONArray()
            channels.forEach { arr.put(it.toJson()) }
            return arr
        }

        fun listFromJson(arr: JSONArray): List<Channel> =
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }
}
