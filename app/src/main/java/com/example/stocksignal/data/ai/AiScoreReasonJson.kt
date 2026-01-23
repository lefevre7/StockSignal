package com.example.stocksignal.data.ai

import com.example.stocksignal.domain.model.AiScoreReason
import org.json.JSONArray
import org.json.JSONObject

object AiScoreReasonJson {

    fun toJson(reasons: List<AiScoreReason>): String {
        val array = JSONArray()
        reasons.forEach { reason ->
            val json = JSONObject()
            json.put("title", reason.title)
            json.put("detail", reason.detail)
            array.put(json)
        }
        return array.toString()
    }

    fun fromJson(raw: String?): List<AiScoreReason> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            AiScoreReason(
                title = json.optString("title").trim(),
                detail = json.optString("detail").trim()
            )
        }
    }
}
