package com.petfilament.recycler.ble;

import java.util.UUID;

public final class BleConstants {

    public static final UUID SERVICE_UUID =
            UUID.fromString("94a9c8c1-9b2c-41e6-a662-e5728e07008a");
    public static final UUID CONTROL_UUID =
            UUID.fromString("94a9c8c1-9b2c-41e6-a662-e5728e07008b");
    public static final UUID STATUS_UUID =
            UUID.fromString("94a9c8c1-9b2c-41e6-a662-e5728e07008c");
    public static final UUID LOG_UUID =
            UUID.fromString("94a9c8c1-9b2c-41e6-a662-e5728e07008d");

    public static final UUID CLIENT_CONFIG_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final String DEVICE_NAME_PREFIX = "PET-Recycle";

    public static final int TEMP_MIN = 0;
    public static final int TEMP_MAX = 300;
    public static final int SPEED_MIN = 0;
    public static final int SPEED_MAX = 4096;

    public static final long SCAN_TIMEOUT_MS = 8_000L;
    public static final long CONNECT_TIMEOUT_MS = 15_000L;
    public static final long STATUS_POLL_INTERVAL_MS = 3_000L;
    public static final int REQUESTED_MTU = 517;

    public static final String CMD_START = "START";
    public static final String CMD_STOP = "STOP";
    public static final String CMD_GET_STATUS = "GET_STATUS";
    public static final String CMD_SET_TEMP_PREFIX = "SET_TEMP:";
    public static final String CMD_SET_SPEED_PREFIX = "SET_SPEED:";

    private BleConstants() {
    }
}