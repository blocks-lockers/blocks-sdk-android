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

    sealed class BluetoothError {
        /// Another operation is already in progress
        class OPERATION_IN_PROGRESS : BluetoothError()
        /// BLE is not ready (no authorization or not powered on)
        class BLE_NOT_READY(val exception: java.lang.IllegalStateException) : BluetoothError()
        /// Blocks not found nearby
        class BLOCKS_NOT_FOUND : BluetoothError()
        /// Blocks found, but connection failed
        class CONNECTION_ERROR : BluetoothError()
        /// Package not found in Blocks
        class PACKAGE_NOT_FOUND : BluetoothError()
        /// Box did not open
        class BOX_NOT_OPENED : BluetoothError()
        /// Internal error
        class INTERNAL_ERROR : BluetoothError()
    }

    sealed class PickupState {
        object Connected : PickupState()
        object Finished : PickupState()
        class Error(val error: BluetoothError) : PickupState()
    }

    private val jsonCoder = Json { ignoreUnknownKeys = true }

    private var pickupHandler: ((PickupState) -> Unit)? = null

    private suspend fun readState(peripheral: Peripheral): BlocksState? {
        return try {
            val bytes = peripheral.read(statusCharacteristic)
            val jsonString = String(bytes)
            jsonCoder.decodeFromString(jsonString)
        } catch (e: Throwable) {
            null
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
                disconnect(peripheral, null)
            }
            BlocksStateEnum.ERROR -> {
                when (state.error) {
                    "PACKAGE_NOT_FOUND" -> {
                        disconnect(peripheral, BluetoothError.PACKAGE_NOT_FOUND())
                    }
                    "BOX_NOT_OPENED" -> {
                        disconnect(peripheral, BluetoothError.BOX_NOT_OPENED())
                    }
                    else -> {
                        disconnect(peripheral, BluetoothError.INTERNAL_ERROR())
                    }
                }
            }
            BlocksStateEnum.READY -> {
                peripheral.write(commandCharacteristic, command.toByteArray(), WriteType.WithResponse)
                delay(1000L)
                pickupAndCheckState(peripheral, command, isOpened)
            }
            else -> { // OPENING, UNKNOWN
                delay(500L)
                pickupAndCheckState(peripheral, command, isOpened)
            }
        }
    }

    private suspend fun disconnect(peripheral: Peripheral, error: BluetoothError?) {
        peripheral.disconnect()
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

        try {
            val advertisement = withTimeout(5000L) {
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
                        pickupHandler?.invoke(PickupState.Error(BluetoothError.BLOCKS_NOT_FOUND()))
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
        } catch (e: java.lang.IllegalStateException) {
            withContext(Dispatchers.Main) {
                pickupHandler?.invoke(PickupState.Error(BluetoothError.BLE_NOT_READY(e)))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            withContext(Dispatchers.Main) {
                pickupHandler?.invoke(PickupState.Error(BluetoothError.BLOCKS_NOT_FOUND()))
            }
        } catch (e: com.juul.kable.ConnectionLostException) {
            withContext(Dispatchers.Main) {
                pickupHandler?.invoke(PickupState.Error(BluetoothError.CONNECTION_ERROR()))
            }
        }
    }

}
