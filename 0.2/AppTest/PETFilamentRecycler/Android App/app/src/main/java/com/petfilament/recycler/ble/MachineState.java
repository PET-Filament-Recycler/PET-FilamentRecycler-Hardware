package com.petfilament.recycler.ble;

public class MachineState {

    public static final String STATUS_OFF = "OFF";
    public static final String STATUS_ON = "ON";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private float temperature = Float.NaN;
    private int speed = -1;
    private String status = STATUS_UNKNOWN;

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public int getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOn() {
        return STATUS_ON.equalsIgnoreCase(status);
    }

    public boolean hasTemperature() {
        return !Float.isNaN(temperature);
    }

    public boolean hasSpeed() {
        return speed >= 0;
    }

    public boolean hasStatus() {
        return status != null && !STATUS_UNKNOWN.equals(status);
    }
}