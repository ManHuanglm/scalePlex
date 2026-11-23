package com.scaleplex.communication

import com.scaleplex.core.model.CommConfig
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.SerialParams

/** Builds the correct [Transport] for a configured [ScaleDevice]. */
object TransportFactory {
    fun create(device: ScaleDevice, bus: CommEventBus, comm: CommConfig = device.comm): Transport =
        when (device.commType) {
            com.scaleplex.core.model.CommType.TCP ->
                TcpClient(device.ip, device.port, comm, bus)
            com.scaleplex.core.model.CommType.UDP ->
                UdpClient(device.ip, device.port, comm, bus)
            com.scaleplex.core.model.CommType.SERIAL ->
                SerialClient(SerialParams(device.serial.portName, device.serial.baudRate, device.serial.dataBits, device.serial.parity, device.serial.stopBits), comm, bus)
        }
}
