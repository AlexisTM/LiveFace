/*
 * Copyright 2020 Google LLC. All rights reserved.
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

package com.google.mlkit.vision.demo.kotlin.facedetector

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.kotlin.DatagramServer
import com.google.mlkit.vision.demo.kotlin.VisionProcessorBase
import com.google.mlkit.vision.face.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/** Face Detector Demo.  */
class FaceDetectorProcessor(context: Context, detectorOptions: FaceDetectorOptions?) :
    VisionProcessorBase<List<Face>>(context) {

    private val detector: FaceDetector

    init {
        val options = detectorOptions
            ?: FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build()

        detector = FaceDetection.getClient(options)

        Log.v(MANUAL_TESTING_LOG, "Face detector options: $options")
    }

    override fun stop() {
        super.stop()
        detector.close()
    }

    override fun detectInImage(image: InputImage): Task<List<Face>> {
        return detector.process(image)
    }

    override fun onSuccess(faces: List<Face>, graphicOverlay: GraphicOverlay) {
        val json = JSONObject();
        val facesJson = JSONArray();
        for (face in faces) {
            val faceJson = JSONObject();
            val boundingBoxJson = JSONObject()
            boundingBoxJson.put("bottom", face.boundingBox.bottom)
            boundingBoxJson.put("top", face.boundingBox.top)
            boundingBoxJson.put("left", face.boundingBox.left)
            boundingBoxJson.put("right", face.boundingBox.right)
            faceJson.put("bounding_box", boundingBoxJson)

            val eulerJson = JSONObject()
            eulerJson.put("x", face.headEulerAngleX)
            eulerJson.put("y", face.headEulerAngleY)
            eulerJson.put("z", face.headEulerAngleZ)
            faceJson.put("euler", eulerJson)

            faceJson.put("id", face.trackingId)
            faceJson.put("smiling", face.smilingProbability)
            faceJson.put("left_eye_open", face.leftEyeOpenProbability)
            faceJson.put("right_eye_open", face.rightEyeOpenProbability)

            val contoursJson = JSONObject()

            for (contourType in contourTypes.indices) {
                val pointsJson = JSONArray()
                val contour = face.getContour(contourTypes[contourType])
                if (contour != null) {
                    for (point in contour.points) {
                        val pointJson = JSONObject()
                        pointJson.put("x", point.x);
                        pointJson.put("y", point.y);
                        pointsJson.put(pointJson)
                    }
                }
                contoursJson.put(contourTypesStrings[contourType], pointsJson)
            }
            faceJson.put("contours", contoursJson)

            val landmarksJson = JSONObject()
            for (landmarkType in landMarkTypes.indices) {
                val landmark = face.getLandmark(landmarkType)
                if (landmark != null) {
                    val landmarkJson = JSONObject()
                    landmarkJson.put("x", landmark.position.x)
                    landmarkJson.put("y", landmark.position.y)
                    landmarksJson.put(landMarkTypesStrings[landmarkType], landmarkJson)
                } else {
                    landmarksJson.put(landMarkTypesStrings[landmarkType], null)
                }
            }
            faceJson.put("landmarks", landmarksJson)

            facesJson.put(faceJson)
        }
        json.put("faces", facesJson)
        DatagramServer.send(graphicOverlay.context, json);

        for (face in faces) {
            graphicOverlay.add(FaceGraphic(graphicOverlay, face))
            logExtrasForTesting(face)
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Face detection failed $e")
    }

    companion object {
        private const val TAG = "FaceDetectorProcessor"

        private val landMarkTypes = intArrayOf(
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EAR,
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.NOSE_BASE
        )

        private val landMarkTypesStrings = arrayOf(
            "mouth_bottom",
            "mouth_right",
            "mouth_left",
            "right_eye",
            "left_eye",
            "right_ear",
            "left_ear",
            "right_cheek",
            "left_cheek",
            "nose_base"
        )

        private val contourTypes = intArrayOf(
            FaceContour.FACE,
            FaceContour.LEFT_EYEBROW_TOP,
            FaceContour.LEFT_EYEBROW_BOTTOM,
            FaceContour.RIGHT_EYEBROW_TOP,
            FaceContour.RIGHT_EYEBROW_BOTTOM,
            FaceContour.LEFT_EYE,
            FaceContour.RIGHT_EYE,
            FaceContour.UPPER_LIP_TOP,
            FaceContour.UPPER_LIP_BOTTOM,
            FaceContour.LOWER_LIP_TOP,
            FaceContour.LOWER_LIP_BOTTOM,
            FaceContour.NOSE_BRIDGE,
            FaceContour.NOSE_BOTTOM,
            FaceContour.LEFT_CHEEK,
            FaceContour.RIGHT_CHEEK,
        )

        private val contourTypesStrings = arrayOf(
            "face",
            "left_eyebrow_top",
            "left_eyebrow_bottom",
            "right_eyebrow_top",
            "right_eyebrow_bottom",
            "left_eye",
            "right_eye",
            "upper_lip_top",
            "upper_lip_bottom",
            "lower_lip_top",
            "lower_lip_bottom",
            "nose_bridge",
            "nose_bottom",
            "left_cheek",
            "right_cheek",
        )

        private fun logExtrasForTesting(face: Face?) {
            if (face != null) {
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face bounding box: " + face.boundingBox.flattenToString()
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face Euler Angle X: " + face.headEulerAngleX
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face Euler Angle Y: " + face.headEulerAngleY
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face Euler Angle Z: " + face.headEulerAngleZ
                )
                // All landmarks
                for (i in landMarkTypes.indices) {
                    val landmark = face.getLandmark(landMarkTypes[i])
                    if (landmark == null) {
                        Log.v(
                            MANUAL_TESTING_LOG,
                            "No landmark of type: " + landMarkTypesStrings[i] + " has been detected"
                        )
                    } else {
                        val landmarkPosition = landmark.position
                        val landmarkPositionStr =
                            String.format(
                                Locale.US,
                                "x: %f , y: %f",
                                landmarkPosition.x,
                                landmarkPosition.y
                            )
                        Log.v(
                            MANUAL_TESTING_LOG,
                            "Position for face landmark: " +
                                    landMarkTypesStrings[i] +
                                    " is :" +
                                    landmarkPositionStr
                        )
                    }
                }
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face left eye open probability: " + face.leftEyeOpenProbability
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face right eye open probability: " + face.rightEyeOpenProbability
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face smiling probability: " + face.smilingProbability
                )
                Log.v(
                    MANUAL_TESTING_LOG,
                    "face tracking id: " + face.trackingId
                )
            }
        }
    }
}
