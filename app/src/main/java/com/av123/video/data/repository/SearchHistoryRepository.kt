package com.av123.video.data.repository

import com.av123.video.data.db.SearchKeywordDao
import com.av123.video.data.db.SearchKeywordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 搜索历史仓库：Room 持久化，最近搜索排在最前。
 * 重复搜索同一关键词（忽略大小写、首尾空白）时删除旧记录后重新置顶，
 * 历史总数上限 [MAX_HISTORY]，超出后淘汰最旧记录。
 */
class SearchHistoryRepository(private val searchKeywordDao: SearchKeywordDao) {

    /** 搜索关键词列表，按搜索时间倒序（最近在前） */
    val keywords: Flow<List<String>> =
        searchKeywordDao.observeRecent(MAX_HISTORY).map { entities ->
            entities.map { it.keyword }
        }

    /** 记录一次搜索：同词去重后置顶，超过上限裁剪最旧记录 */
    suspend fun add(keyword: String) {
        val normalized = keyword.trim().replace(WHITESPACE, " ")
        if (normalized.isEmpty()) return
        // 删除已存在的同词记录（忽略大小写），再置顶插入
        searchKeywordDao.deleteIgnoreCase(normalized)
        searchKeywordDao.upsert(
            SearchKeywordEntity(
                keyword = normalized,
                searchedAt = System.currentTimeMillis()
            )
        )
        val count = searchKeywordDao.count()
        if (count > MAX_HISTORY) {
            searchKeywordDao.deleteOldest(count - MAX_HISTORY)
        }
    }

    /** 删除单条历史（点击关键词尾部 X） */
    suspend fun delete(keyword: String) {
        searchKeywordDao.delete(keyword)
    }

    /** 清空全部搜索历史 */
    suspend fun clear() {
        searchKeywordDao.clear()
    }

    private companion object {
        const val MAX_HISTORY = 20
        val WHITESPACE = Regex("\\s+")
    }
}
