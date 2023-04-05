/*
 * Copyright 2022 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mlkit.vision.demo.kotlin.facemeshdetector

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.kotlin.DatagramServer
import com.google.mlkit.vision.demo.kotlin.VisionProcessorBase
import com.google.mlkit.vision.demo.preference.PreferenceUtils
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import org.json.JSONArray
import org.json.JSONObject

/** Face Mesh Detector Demo. */
class FaceMeshDetectorProcessor(context: Context) :
    VisionProcessorBase<List<FaceMesh>>(context) {

    private val detector: FaceMeshDetector

    init {
        val optionsBuilder = FaceMeshDetectorOptions.Builder()
        if (PreferenceUtils.getFaceMeshUseCase(context) == FaceMeshDetectorOptions.BOUNDING_BOX_ONLY) {
            optionsBuilder.setUseCase(FaceMeshDetectorOptions.BOUNDING_BOX_ONLY)
        }
        detector = FaceMeshDetection.getClient(optionsBuilder.build())
    }

    override fun stop() {
        super.stop()
        detector.close()
    }

    override fun detectInImage(image: InputImage): Task<List<FaceMesh>> {
        return detector.process(image)
    }

    override fun onSuccess(faces: List<FaceMesh>, graphicOverlay: GraphicOverlay) {
        val json = JSONObject()
        for (face in faces) {
            val faceJson = JSONObject()

            val pointsJson = JSONArray()
            for (point in face.allPoints) {
                val pointJson = JSONObject()
                pointJson.put("x", point.position.x);
                pointJson.put("y", point.position.y);
                pointJson.put("z", point.position.z);
                pointsJson.put(pointJson)
            }
            faceJson.put("points", pointsJson)

            /*
            val triangleJson = JSONArray()
            for(triangle in face.allTriangles) {
                val trianglePointsJson = JSONArray()
                for (point in triangle.allPoints) {
                    val pointJson = JSONObject()
                    pointJson.put("x", point.position.x);
                    pointJson.put("y", point.position.y);
                    pointJson.put("z", point.position.z);
                    trianglePointsJson.put(pointJson)
                }
                triangleJson.put(trianglePointsJson)
            }
            faceJson.put("triangles", triangleJson);
             */

            val boundingBoxJson = JSONObject()
            boundingBoxJson.put("bottom", face.boundingBox.bottom)
            boundingBoxJson.put("top", face.boundingBox.top)
            boundingBoxJson.put("left", face.boundingBox.left)
            boundingBoxJson.put("right", face.boundingBox.right)
            faceJson.put("bounding_box", boundingBoxJson)

            json.put("faces", faceJson)

            graphicOverlay.add(FaceMeshGraphic(graphicOverlay, face))
        }
        DatagramServer.send(graphicOverlay.context, json);
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Face detection failed $e")
    }

    companion object {
        private const val TAG = "SelfieFaceProcessor"
    }
}
