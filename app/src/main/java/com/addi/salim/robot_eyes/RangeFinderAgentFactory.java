package com.addi.salim.robot_eyes;

public class RangeFinderAgentFactory {
    private final int imageWidth;
    private final int imageHeight;
    private final double angleOfView;
    private final double distanceToRangeFinder; // cm
    private final RangeFinderManager rangeFinderManager;

    public RangeFinderAgentFactory(int imageWidth, int imageHeight, double angleOfView, double distanceToRangeFinder, RangeFinderManager rangeFinderManager) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.angleOfView = angleOfView;
        this.distanceToRangeFinder = distanceToRangeFinder;
        this.rangeFinderManager = rangeFinderManager;
    }

    public RangeFinderAgent createNewAgent(FaceGraphic graphic) {
        return new RangeFinderAgent(imageWidth, imageHeight, angleOfView, distanceToRangeFinder, graphic, rangeFinderManager);
    }
}
