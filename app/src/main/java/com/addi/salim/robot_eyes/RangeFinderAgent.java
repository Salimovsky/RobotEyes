package com.addi.salim.robot_eyes;

import android.util.Log;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;

public class RangeFinderAgent extends Tracker<Face> implements ObjectRangeListener {
    private final int imageWidth;
    private final int imageHeight;
    private final double angleOfView;
    private final double verticalDistance;
    private final double distanceToRangeFinder; // distance between camera lends and servo motor in cm
    private final FaceGraphic graphic;
    private final RangeFinderManager rangeFinderManager;
    private int id;

    public RangeFinderAgent(int imageWidth, int imageHeight, double angleOfView, double distanceToRangeFinder, FaceGraphic graphic, RangeFinderManager rangeFinderManager) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.angleOfView = angleOfView;
        this.distanceToRangeFinder = distanceToRangeFinder;
        this.graphic = graphic;
        this.rangeFinderManager = rangeFinderManager;
        this.verticalDistance = ((double) (((double) imageWidth) / 2.d)) / (double) Math.tan(Math.toRadians(angleOfView) / 2.d); //505.55
        Log.e("****", " verticalDistance = " + verticalDistance + " imageWidth = " + imageWidth + " angleOfView = " + angleOfView);
    }

    @Override
    public void onNewItem(int id, Face item) {
        this.id = id;
        rangeFinderManager.onNewFaceAdded(id, this);
    }

    @Override
    public void onUpdate(Detector.Detections<Face> detections, Face face) {
        // assuming CAMERA_FACING_BACK
        final float faceCenterX = face.getPosition().x + face.getWidth() / 2;
        final float faceCenterY = face.getPosition().y + face.getHeight() / 2;

        final double motorAngleToObject = Math.toDegrees(Math.atan((((double) imageWidth / 2d) - faceCenterX) / (verticalDistance - distanceToRangeFinder)));

        rangeFinderManager.estimateDistanceRange(id, motorAngleToObject);
    }

    @Override
    public void onMissing(Detector.Detections<Face> detections) {
    }

    @Override
    public void onDone() {
        rangeFinderManager.onFaceRemoved(id);
    }

    @Override
    public void onDistanceUpdated(int id, int distanceInCentimeter) {
        if (id != this.id) {
            return;
        }

        graphic.updateItemDistance(distanceInCentimeter);
    }
}
