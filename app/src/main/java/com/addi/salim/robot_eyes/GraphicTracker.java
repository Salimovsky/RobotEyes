/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addi.salim.robot_eyes;

import com.addi.salim.robot_eyes.camera.GraphicOverlay;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;

/**
 * Generic tracker which is used for tracking either a face or a barcode (and can really be used for
 * any type of item).  This is used to receive newly detected items, add a graphical representation
 * to an overlay, update the graphics as the item changes, and remove the graphics when the item
 * goes away.
 */
class GraphicTracker extends Tracker<Face> {
    private GraphicOverlay mOverlay;
    private TrackedGraphic mGraphic;
    private RangeFinderAgent rangeFinderAgent;

    GraphicTracker(GraphicOverlay overlay, TrackedGraphic graphic, RangeFinderAgent rangeFinderAgent) {
        mOverlay = overlay;
        mGraphic = graphic;
        this.rangeFinderAgent = rangeFinderAgent;
    }

    /**
     * Start tracking the detected item instance within the item overlay.
     */
    @Override
    public void onNewItem(int id, Face item) {
        mGraphic.setId(id);
        rangeFinderAgent.onNewItem(id, item);
    }

    /**
     * Update the position/characteristics of the item within the overlay.
     */
    @Override
    public void onUpdate(Detector.Detections<Face> detectionResults, Face item) {
        mOverlay.add(mGraphic);
        mGraphic.updateItem(item);
        rangeFinderAgent.onUpdate(detectionResults, item);
    }

    /**
     * Hide the graphic when the corresponding face was not detected.  This can happen for
     * intermediate frames temporarily, for example if the face was momentarily blocked from
     * view.
     */
    @Override
    public void onMissing(Detector.Detections<Face> detectionResults) {
        mOverlay.remove(mGraphic);
        rangeFinderAgent.onMissing(detectionResults);
    }

    /**
     * Called when the item is assumed to be gone for good. Remove the graphic annotation from
     * the overlay.
     */
    @Override
    public void onDone() {
        mOverlay.remove(mGraphic);
        rangeFinderAgent.onDone();
    }
}
