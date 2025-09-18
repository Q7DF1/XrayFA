package com.android.v2rayForAndroidUI.repository

import android.util.Log
import com.android.v2rayForAndroidUI.dao.LinkDao
import com.android.v2rayForAndroidUI.model.Link
import kotlinx.coroutines.flow.Flow

class LinkRepository(private val linkDao: LinkDao){
    val allLinks = linkDao.getAllLinks()

    suspend fun addLink(link: Link) {
        Log.i("lishien", "addLink: $link")
        linkDao.addLink(link)
    }

    suspend fun deleteLink(link: Link) {
        linkDao.deleteLink(link)
    }

    fun loadLinksById(id: Int): Flow<Link> {
        return linkDao.loadLinksById(id)
    }

    suspend fun deleteLinkById(id: Int) {
        return linkDao.deleteLinkById(id)
    }
}