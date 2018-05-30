package com.addi.salim.robot_eyes;

public interface ConnectionListener {
    void onConnected();

    void onDisconnected();

    void onConnectionError();

    void onDistanceReceived(int id, int distanceInCm);

    void onAlarmReceived();
}
