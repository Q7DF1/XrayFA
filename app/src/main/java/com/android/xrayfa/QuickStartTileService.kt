package com.android.xrayfa

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

class QuickStartTileService
@Inject constructor(
    private val xrayBaseServiceManager: XrayBaseServiceManager
): TileService() {

    companion object {
        const val TAG = "QuickStartTileService"
    }
    private val serviceScope =  CoroutineScope(SupervisorJob() + Dispatchers.Main)


    init {

        xrayBaseServiceManager.qsStateCallBack = { running ->
            qsTile.state = if (running)
                Tile.STATE_ACTIVE
            else
                Tile.STATE_INACTIVE
            qsTile.updateTile()
        }
    }
    override fun onClick() {
        if (!XrayBaseService.isRunning) {
            serviceScope.launch {
                if (!xrayBaseServiceManager.startXrayBaseService(applicationContext)) {
                    Toast.makeText(applicationContext,R.string.config_not_ready,Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }else {
            xrayBaseServiceManager.stopXrayBaseService(applicationContext)
        }

        // stop Service
    }

    override fun onTileAdded() {

        super.onTileAdded()
    }

    override fun onDestroy() {
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
        super.onDestroy()
    }

}