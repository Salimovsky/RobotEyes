package com.addi.salim.robot_eyes;

import android.app.Application;

public class MyApplication extends Application {
    private ArduinoManager arduinoManagerSingleton;

    @Override
    public void onCreate() {
        super.onCreate();
        arduinoManagerSingleton = ArduinoManager.getInstance(this);
        arduinoManagerSingleton.initialize();
    }
}
