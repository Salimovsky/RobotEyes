package com.addi.salim.night_light;

public interface ConnectionListener {
    void onConnected();

    void onDisconnected();

    void onConnectionError();

    void onDataReceived(byte[] data);
}
