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
        implementation 'com.github.blocks-lockers:blocks-sdk-android:0.1.0'
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
                is BlocksBluetoothManager.PickupState.Opened -> Toast.makeText(requireContext(), "Opened", Toast.LENGTH_SHORT).show()
                is BlocksBluetoothManager.PickupState.Finished -> Toast.makeText(requireContext(), "Finished", Toast.LENGTH_SHORT).show()
                is BlocksBluetoothManager.PickupState.Error -> Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show()
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