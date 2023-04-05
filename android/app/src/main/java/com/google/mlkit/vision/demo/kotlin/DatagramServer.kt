package com.google.mlkit.vision.demo.kotlin

import android.content.Context
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

public object DatagramServer {
    private val socket: DatagramSocket = DatagramSocket()
    private var queue: BlockingQueue<DatagramPacket> = LinkedBlockingQueue<DatagramPacket>()

    init {
        thread(start = true, isDaemon = false, name = "datagramSender") {
            try {
                while (true) {
                    val packet = queue.take()
                    socket.send(packet)
                }
            } catch (ex: InterruptedException) {

            }
        }
    }

    public fun send(context: Context, json: JSONObject) {
        val ip = PreferenceUtils.getIp(context);
        val port = PreferenceUtils.getPort(context);
        val string = json.toString()
        val buf = string.toByteArray()
        val packet = DatagramPacket(buf, buf.size, InetAddress.getByName(ip), port)
        queue.add(packet)
    }
}