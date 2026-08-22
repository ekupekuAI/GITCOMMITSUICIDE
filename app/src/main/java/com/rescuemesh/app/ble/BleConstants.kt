package com.rescuemesh.app.ble

import android.os.ParcelUuid
import java.util.UUID

object BleConstants {
    const val LOCAL_NAME = "RMESH"

    val MeshServiceUuid: UUID = UUID.fromString("8E400001-F315-4F60-9FB8-838830DAEA50")
    val ControlCharacteristicUuid: UUID = UUID.fromString("8E400002-F315-4F60-9FB8-838830DAEA50")
    val DataRxCharacteristicUuid: UUID = UUID.fromString("8E400003-F315-4F60-9FB8-838830DAEA50")
    val DataTxCharacteristicUuid: UUID = UUID.fromString("8E400004-F315-4F60-9FB8-838830DAEA50")

    val MeshServiceParcelUuid: ParcelUuid = ParcelUuid(MeshServiceUuid)
}
