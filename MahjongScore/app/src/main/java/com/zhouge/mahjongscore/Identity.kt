package com.zhouge.mahjongscore

import android.content.Context
import java.util.UUID

object Identity {
    private const val PREFS = "mj_prefs"
    private const val KEY_ID = "player_id"
    private const val KEY_NAME = "player_name"

    fun ensureId(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = p.getString(KEY_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            p.edit().putString(KEY_ID, id).apply()
        }
        return id
    }

    fun loadName(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun saveName(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, name).apply()
    }

    fun saveLastRoom(ctx: Context, room: String, startTs: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_room", room)
            .putLong("last_room_start", startTs)
            .apply()
    }

    fun loadLastRoom(ctx: Context): String =
        if (isRejoinValid(ctx))
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last_room", "") ?: ""
        else ""

    fun clearLastRoom(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("last_room").remove("last_room_start").apply()
    }

    /** 回到房间：自开局起 24 小时内有效 */
    fun isRejoinValid(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val room = p.getString("last_room", "") ?: ""
        val start = p.getLong("last_room_start", 0L)
        if (room.isEmpty()) return false
        if (start <= 0) return false
        val now = System.currentTimeMillis() / 1000
        return now - start < 24 * 3600
    }

    // ---- 计分规则偏好（建房时带出去，房内可再改） ----

    private const val KEY_SCORES = "custom_scores"

    /** 保存分值偏好；JSON 一行 {"pinghu":5,...}，非法内容忽略 */
    fun saveScores(ctx: Context, scores: Map<String, Long>) {
        val json = org.json.JSONObject().apply {
            scores.forEach { (k, v) -> put(k, v) }
        }.toString()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCORES, json).apply()
    }

    /** 读取分值偏好；没有保存过返回 null（用默认表） */
    fun loadScores(ctx: Context): Map<String, Long>? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCORES, null) ?: return null
        return try {
            val o = org.json.JSONObject(raw)
            val m = mutableMapOf<String, Long>()
            KIND_POINTS.keys.forEach { k ->
                if (o.has(k)) m[k] = o.getLong(k)
            }
            if (m.isEmpty()) null else m
        } catch (e: Exception) {
            null
        }
    }
}
