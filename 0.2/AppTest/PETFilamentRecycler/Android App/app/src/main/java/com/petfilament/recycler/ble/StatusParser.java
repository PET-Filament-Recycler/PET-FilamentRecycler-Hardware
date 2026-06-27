package com.petfilament.recycler.ble;

import android.util.Log;

import java.util.Locale;

public final class StatusParser {

    private static final String TAG = "StatusParser";

    private StatusParser() {
    }

    public static MachineState parse(String rawData) {
        MachineState state = new MachineState();
        if (rawData == null) {
            return state;
        }

        String data = sanitize(rawData);
        if (data.isEmpty()) {
            return state;
        }

        if (!data.contains("TEMP:") && !data.contains("SPEED:") && !data.contains("STATUS:")) {
            return state;
        }

        String[] parts = data.split(",");
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith("TEMP:")) {
                parseTemperature(item, state);
            } else if (item.startsWith("SPEED:")) {
                parseSpeed(item, state);
            } else if (item.startsWith("STATUS:")) {
                parseStatus(item, state);
            }
        }

        if (!state.hasStatus()) {
            parseStatusFieldFallback(data, state);
        }

        return state;
    }

    private static String sanitize(String rawData) {
        return rawData
                .replace("\u0000", "")
                .replaceAll("[\\x00-\\x1F\\x7F]", "")
                .trim();
    }

    private static void parseStatusFieldFallback(String data, MachineState state) {
        String upper = data.toUpperCase(Locale.US);
        int index = upper.indexOf("STATUS:");
        if (index < 0) {
            return;
        }

        String remainder = data.substring(index + "STATUS:".length()).trim();
        int commaIndex = remainder.indexOf(',');
        if (commaIndex >= 0) {
            remainder = remainder.substring(0, commaIndex).trim();
        }

        applyStatusValue(remainder, state);
    }

    private static void parseTemperature(String item, MachineState state) {
        String value = item.substring("TEMP:".length()).trim();
        if (value.equalsIgnoreCase("ERR")) {
            return;
        }
        try {
            state.setTemperature(Float.parseFloat(value));
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid temperature: " + item);
        }
    }

    private static void parseSpeed(String item, MachineState state) {
        String value = item.substring("SPEED:".length()).trim();
        try {
            state.setSpeed(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            Log.w(TAG, "Invalid speed: " + item);
        }
    }

    private static void parseStatus(String item, MachineState state) {
        applyStatusValue(item.substring("STATUS:".length()).trim(), state);
    }

    private static void applyStatusValue(String value, MachineState state) {
        if (value.isEmpty()) {
            return;
        }

        if (value.equalsIgnoreCase(MachineState.STATUS_ON) || value.equalsIgnoreCase("1")) {
            state.setStatus(MachineState.STATUS_ON);
        } else if (value.equalsIgnoreCase(MachineState.STATUS_OFF) || value.equalsIgnoreCase("0")) {
            state.setStatus(MachineState.STATUS_OFF);
        } else {
            state.setStatus(value.toUpperCase(Locale.US));
        }
    }
}