package com.blockslockers.sdk

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import org.altbeacon.beacon.*

class BlocksLocationManager: BeaconConsumer {

    companion object {

        const val IBEACON = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
        const val REGION_UUID = "107d6776-1c08-4f37-b4a6-a244c1e54127"
        val region = Region(REGION_UUID, null, null, null)

    }

    private val context: Context
    private val beaconManager: BeaconManager
    private var rangingHandler: ((List<String>) -> Unit)? = null

    constructor(context: Context) {
        this.context = context

        beaconManager = BeaconManager.getInstanceForApplication(context)
        beaconManager.beaconParsers?.add(BeaconParser().setBeaconLayout(IBEACON))
        beaconManager.backgroundScanPeriod = 1100
        beaconManager.backgroundBetweenScanPeriod = 0
        beaconManager.bind(this)
    }

    fun startRanging(handler: ((List<String>) -> Unit)) {
        rangingHandler = handler
        beaconManager.startRangingBeaconsInRegion(region)
    }

    fun stopRanging() {
        rangingHandler = null
        beaconManager.stopRangingBeaconsInRegion(region)
        beaconManager.unbind(this)
    }

    /*
     * BeaconConsumer
     */

    override fun onBeaconServiceConnect() {
        beaconManager.removeAllRangeNotifiers()
        beaconManager.addRangeNotifier { beacons, _ ->
            val filteredBeacons = beacons.filter { it.distance <= 5 }
            val serialNumbers = filteredBeacons.map { String.format("%04d-%04d", it.id2.toInt(), it.id3.toInt()) }
            rangingHandler?.invoke(serialNumbers)
        }
    }

    override fun getApplicationContext(): Context {
        return context
    }

    override fun bindService(intent: Intent, serviceConnection: ServiceConnection, flags: Int): Boolean {
        return context.bindService(intent, serviceConnection, flags)
    }

    override fun unbindService(serviceConnection: ServiceConnection) {
        context.unbindService(serviceConnection)
    }

}
