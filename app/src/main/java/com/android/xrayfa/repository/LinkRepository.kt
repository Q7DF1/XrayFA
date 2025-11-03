package com.android.xrayfa.repository

import com.android.xrayfa.dao.LinkDao
import com.android.xrayfa.dto.Link
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkRepository @Inject constructor(
    private val linkDao: LinkDao
){
    val allLinks = linkDao.getAllLinks()

    suspend fun addLink(vararg links: Link) {
        linkDao.addLink(*links)
    }

    suspend fun deleteLink(link: Link) {
        linkDao.deleteLink(link)
    }

    fun loadLinksById(id: Int): Flow<Link> {
        return linkDao.loadLinksById(id)
    }
    suspend fun clearSelection() {
        return linkDao.clearSelection()
    }

     fun querySelectedLink(): Flow<Link?> {
        return linkDao.querySelectedLink()
    }

    suspend fun updateLink(link: Link) {
        return linkDao.updateLink(link)
    }

    suspend fun updateLinkById(id: Int, selected: Boolean) {
        return linkDao.updateLinkById(id,selected)
    }

    suspend fun deleteLinkById(id: Int) {
        return linkDao.deleteLinkById(id)
    }

    suspend fun deleteLinkBySubscriptionId(subscriptionId: Int) {
        return linkDao.deleteBySubscriptionId(subscriptionId)
    }
}