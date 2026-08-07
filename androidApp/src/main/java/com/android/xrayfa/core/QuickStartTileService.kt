package com.android.xrayfa.core

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.android.xrayfa.R
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuickStartTileService(
    private val vpnController: VpnController,
) : TileService() {

    companion object {
        const val TAG = "QuickStartTileService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        serviceScope.launch {
            vpnController.state.collect { state ->
                qsTile.state = if (state.isConnected) {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }
                qsTile.updateTile()
            }
        }
    }

    override fun onClick() {
        if (!vpnController.state.value.isConnected) {
            serviceScope.launch {
                if (!vpnController.connect()) {
                    Toast.makeText(
                        applicationContext,
                        R.string.config_not_ready,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            vpnController.disconnect()
        }
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
