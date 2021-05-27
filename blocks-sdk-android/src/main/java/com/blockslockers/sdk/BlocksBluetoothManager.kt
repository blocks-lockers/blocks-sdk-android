package com.blockslockers.sdk

import com.benasher44.uuid.uuidFrom
import com.blockslockers.sdk.model.BlocksState
import com.blockslockers.sdk.model.BlocksStateEnum
import com.juul.kable.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.first

/**
 * Created by Alex Studnička on 12/05/2021.
 * Copyright © 2021 Property Blocks s.r.o. All rights reserved.
 */

class BlocksBluetoothManager {

    companion object {

        private const val serviceId                 = "aa2fbfff-4f1c-4855-a626-5f4b7bba09a2"
        private const val statusCharacteristicId    = "aa2fbfff-4f1c-4855-a626-5f4b7bba09a3"
        private const val commandCharacteristicId   = "aa2fbfff-4f1c-4855-a626-5f4b7bba09a4"
        private val statusCharacteristic = characteristicOf(serviceId, statusCharacteristicId)
        private val commandCharacteristic = characteristicOf(serviceId, commandCharacteristicId)

    }

    enum class BluetoothError {
        BLOCKS_MISMATCH,
        PICKUP_ERROR
    }

    sealed class PickupState {
        object Connected : PickupState()
        object Opened : PickupState()
        object Finished : PickupState()
        class Error(val error: BluetoothError) : PickupState()
    }

    private var pickupHandler: ((PickupState) -> Unit)? = null

    private suspend fun readState(peripheral: Peripheral): BlocksState? {
        try {
            val bytes = peripheral.read(statusCharacteristic)
            val jsonString = String(bytes)
            return Json { ignoreUnknownKeys = true }.decodeFromString(jsonString)
        } catch (e: Throwable) {
            return null
        }
    }

    private suspend fun pickupAndCheckState(peripheral: Peripheral, command: String, isOpened: Boolean = false) {
        val state = readState(peripheral)

        if (state == null) {
            delay(100L)
            pickupAndCheckState(peripheral, command, isOpened)
            return
        }

        when (state.state) {
            BlocksStateEnum.FINISHED -> {
                logout(peripheral, null)
            }
            BlocksStateEnum.ERROR -> {
                logout(peripheral, BluetoothError.PICKUP_ERROR)
            }
            BlocksStateEnum.WAITING_FOR_CLOSE -> {
                if (!isOpened) {
                    withContext(Dispatchers.Main) {
                        pickupHandler?.invoke(PickupState.Opened)
                    }
                }
                delay(500L)
                pickupAndCheckState(peripheral, command, true)
            }
            BlocksStateEnum.READY -> {
                peripheral.write(commandCharacteristic, command.toByteArray(), WriteType.WithResponse)
                delay(1000L)
                pickupAndCheckState(peripheral, command, isOpened)
            }
            BlocksStateEnum.UNKNOWN -> {
                delay(500L)
                pickupAndCheckState(peripheral, command, isOpened)
            }
        }
    }

    private suspend fun logout(peripheral: Peripheral, error: BluetoothError?) {
        peripheral.write(commandCharacteristic, "{\"type\":\"logout\"}".toByteArray(), WriteType.WithResponse)
        // Allow 5 seconds for graceful disconnect before forcefully closing `Peripheral`.
        withTimeoutOrNull(5_000L) {
            peripheral.disconnect()
        }
        withContext(Dispatchers.Main) {
            if (error != null) {
                pickupHandler?.invoke(PickupState.Error(error))
            } else {
                pickupHandler?.invoke(PickupState.Finished)
            }
        }
    }

    suspend fun pickupPackage(packageId: String, unlockCode: String, blocksSerialNo: String, handler: ((PickupState) -> Unit)) {
        pickupHandler = handler

        val advertisement = withTimeout(10000L) {
            Scanner()
                .advertisements
                .first {
                    it.uuids.contains(uuidFrom("aa2fbfff-4f1c-4855-a626-5f4b7bba09a2"))
                }
        }

        val peripheral = GlobalScope.peripheral(advertisement)

        peripheral.connect()

        while (true) {
            val initState = readState(peripheral)

            if (initState == null) {
                delay(100L)
                continue
            }

            if (initState.serialNo != blocksSerialNo) {
                withContext(Dispatchers.Main) {
                    pickupHandler?.invoke(PickupState.Error(BluetoothError.BLOCKS_MISMATCH))
                }
                return
            }

            if (initState.state != BlocksStateEnum.READY) {
                delay(500L)
                continue
            }

            break
        }

        withContext(Dispatchers.Main) {
            pickupHandler?.invoke(PickupState.Connected)
        }

        delay(1000L)

        val command = "{\"type\":\"pickup\",\"package_id\":\"${packageId}\",\"unlock_code\":\"${unlockCode}\"}"

        pickupAndCheckState(peripheral, command)
    }

}
