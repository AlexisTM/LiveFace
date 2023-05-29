package com.google.mediapipe.examples.facelandmarker

import android.util.Log
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.sqrt

import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor

@Serializable
data class Face(val name: String, val blendshapes: Map<String, Float>, val transform: List<Float>)

object DatagramServer {
    private val socket: DatagramSocket = DatagramSocket()
    private var queue: BlockingQueue<FaceLandmarkerResult> = LinkedBlockingQueue()

    init {
        thread(start = true, isDaemon = false, name = "datagramSender") {
            try {
                while (true) {
                    val landmarkResult = queue.take()

                    var blenshapesList = mutableMapOf<String, Float>()
                    if (landmarkResult.faceBlendshapes().isPresent) {
                        val blendshapes = landmarkResult.faceBlendshapes().get()
                        if (blendshapes.isNotEmpty()) {
                            val face = blendshapes[0]
                            face.forEach { blendshape ->
                                blenshapesList.put(blendshape.categoryName(), blendshape.score())
                            }
                        }
                    }

                    // https://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToQuaternion/
                    val transformMatrix = mutableListOf<Float>()
                    if (landmarkResult.facialTransformationMatrixes().isPresent) {
                        val transform = landmarkResult.facialTransformationMatrixes().get()
                        if (transform.isNotEmpty()) {
                            val face = transform[0]
                            face.forEach {
                                transformMatrix.add(it)
                            }
                        }
                    }

                    val data = Face("android", blenshapesList, transformMatrix)
                    val buf = Cbor.encodeToByteArray(data)

                    val packet = DatagramPacket(
                        buf,
                        buf.size,
                        InetAddress.getByName("192.168.178.50"),
                        54321
                    )
                    socket.send(packet)
                }
            } catch (ex: InterruptedException) {

            }
        }
    }
    fun send(result: FaceLandmarkerResult) {
        result.let { faceLandmarkerResult ->
            queue.put(faceLandmarkerResult)
        }
    }
}
