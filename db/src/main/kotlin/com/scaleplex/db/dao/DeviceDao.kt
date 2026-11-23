package com.scaleplex.db.dao

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.core.model.SerialParams
import com.scaleplex.db.Database
import com.scaleplex.db.insert
import com.scaleplex.db.query
import com.scaleplex.db.exec

/** CRUD for `scale_device`. */
class DeviceDao(private val db: Database) {

    fun insert(d: ScaleDevice): Long = db.withDb { c ->
        c.insert(
            """INSERT INTO scale_device (name,brand,model,comm_type,ip,port,serial_port,baud,data_bits,parity,
                stop_bits,connect_timeout,read_timeout,retries,group_id,enabled,note)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            d.name, d.brand.id, d.model, d.commType.id, d.ip, d.port, d.serial.portName, d.serial.baudRate,
            d.serial.dataBits, d.serial.parity, d.serial.stopBits, d.comm.connectTimeoutMs, d.comm.readTimeoutMs,
            d.comm.retries, d.groupId, if (d.enabled) 1 else 0, d.note,
        )
    }

    fun update(d: ScaleDevice): Int = db.withDb { c ->
        c.exec(
            """UPDATE scale_device SET name=?,brand=?,model=?,comm_type=?,ip=?,port=?,serial_port=?,baud=?,
                data_bits=?,parity=?,stop_bits=?,connect_timeout=?,read_timeout=?,retries=?,group_id=?,
                enabled=?,note=? WHERE id=?""",
            d.name, d.brand.id, d.model, d.commType.id, d.ip, d.port, d.serial.portName, d.serial.baudRate,
            d.serial.dataBits, d.serial.parity, d.serial.stopBits, d.comm.connectTimeoutMs, d.comm.readTimeoutMs,
            d.comm.retries, d.groupId, if (d.enabled) 1 else 0, d.note, d.id,
        )
    }

    fun delete(id: Long): Int = db.withDb { it.exec("DELETE FROM scale_device WHERE id=?", id) }

    fun list(groupId: Long? = null): List<ScaleDevice> = db.withDb { c ->
        if (groupId == null) {
            c.query("SELECT * FROM scale_device ORDER BY id") { rs -> map(rs) }
        } else {
            c.query("SELECT * FROM scale_device WHERE group_id=? ORDER BY id", groupId) { rs -> map(rs) }
        }
    }

    fun get(id: Long): ScaleDevice? = db.withDb { c ->
        c.query("SELECT * FROM scale_device WHERE id=?", id) { rs -> map(rs) }.firstOrNull()
    }

    fun countByGroup(groupId: Long): Int = db.withDb { c ->
        c.query("SELECT COUNT(*) n FROM scale_device WHERE group_id=?", groupId) { it.getInt("n") }.firstOrNull() ?: 0
    }

    private fun map(rs: java.sql.ResultSet): ScaleDevice = ScaleDevice(
        id = rs.getLong("id"),
        name = rs.getString("name") ?: "",
        brand = ScaleBrand.fromId(rs.getString("brand") ?: "generic"),
        model = rs.getString("model") ?: "",
        commType = CommType.fromId(rs.getString("comm_type") ?: "udp"),
        ip = rs.getString("ip") ?: "",
        port = rs.getInt("port"),
        serial = SerialParams(
            portName = rs.getString("serial_port") ?: "COM1",
            baudRate = rs.getInt("baud"),
            dataBits = rs.getInt("data_bits"),
            parity = rs.getInt("parity"),
            stopBits = rs.getInt("stop_bits"),
        ),
        comm = com.scaleplex.core.model.CommConfig(
            connectTimeoutMs = rs.getInt("connect_timeout"),
            readTimeoutMs = rs.getInt("read_timeout"),
            retries = rs.getInt("retries"),
        ),
        groupId = rs.getLong("group_id"),
        enabled = rs.getInt("enabled") == 1,
        note = rs.getString("note") ?: "",
    )
}
