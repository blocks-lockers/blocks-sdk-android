# BlocksSDK Android

By [Blocks lockers](https://blockslockers.com/)

## Requirements

* Android API level 21+

## Installation

### Gradle

Blocks SDK for Android is available from jitpack.io.

main build.gradle

    allprojects {
        repositories {
            // ...
            maven { url 'https://jitpack.io' }
        }
    }

app build.gradle

    dependencies {
        implementation 'com.github.blocks-lockers:blocks-sdk-android:1.0.0'
    }

## Usage

Package pick-up via Bluetooth
```kotlin
import com.blockslockers.sdk.BlocksBluetoothManager

val bluetoothManager = BlocksBluetoothManager()

GlobalScope.launch {
    try {
        bluetoothManager?.pickupPackage(packageId, unlockCode, blocksSerialNo) { state ->
            when (state) {
                is BlocksBluetoothManager.PickupState.Connected -> Toast.makeText(requireContext(), "Connected", Toast.LENGTH_SHORT).show()
                is BlocksBluetoothManager.PickupState.Finished -> Toast.makeText(requireContext(), "Finished", Toast.LENGTH_SHORT).show()
                is BluetoothManager.PickupState.Error -> {
                    when (state.error) {
                        BluetoothManager.BluetoothError.OPERATION_IN_PROGRESS -> {
                            Toast.makeText(requireContext(), "Another operation is already in progress", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothManager.BluetoothError.BLE_NOT_READY -> {
                            Toast.makeText(requireContext(), "BLE is not ready (no authorization or not powered on)", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothManager.BluetoothError.BLOCKS_NOT_FOUND -> {
                            Toast.makeText(requireContext(), "Blocks not found nearby", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothManager.BluetoothError.CONNECTION_ERROR -> {
                            Toast.makeText(requireContext(), "Blocks found, but connection failed", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothManager.BluetoothError.PACKAGE_NOT_FOUND -> {
                            Toast.makeText(requireContext(), "Package not found in Blocks", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothManager.BluetoothError.BOX_NOT_OPENED -> {
                            Toast.makeText(requireContext(), "Box did not open", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothManager.BluetoothError.INTERNAL_ERROR -> {
                            Toast.makeText(requireContext(), "Internal error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "Exception", Toast.LENGTH_SHORT).show()
        }
    }
}
```

## Author

* [Blocks lockers](https://github.com/blocks-lockers)