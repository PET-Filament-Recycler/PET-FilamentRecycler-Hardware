package com.petfilament.recycler;

import com.petfilament.recycler.ble.MachineState;
import com.petfilament.recycler.ble.StatusParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusParserTest {

    @Test
    public void parseCombinedStatus() {
        MachineState state = StatusParser.parse("TEMP:50,SPEED:1000,STATUS:ON");

        assertEquals(50f, state.getTemperature(), 0.01f);
        assertEquals(1000, state.getSpeed());
        assertEquals(MachineState.STATUS_ON, state.getStatus());
        assertTrue(state.isOn());
    }

    @Test
    public void parseOffStatus() {
        MachineState state = StatusParser.parse("TEMP:0,SPEED:0,STATUS:OFF");

        assertEquals(MachineState.STATUS_OFF, state.getStatus());
        assertTrue(state.hasStatus());
    }

    @Test
    public void ignoresNonStatusLogMessages() {
        MachineState state = StatusParser.parse("BLE client connected");

        assertFalse(state.hasTemperature());
        assertFalse(state.hasSpeed());
        assertFalse(state.hasStatus());
    }

    @Test
    public void parseStatusWithControlCharacters() {
        MachineState state = StatusParser.parse("TEMP:50,SPEED:1000,STATUS:ON\u0000");

        assertEquals(MachineState.STATUS_ON, state.getStatus());
    }
}