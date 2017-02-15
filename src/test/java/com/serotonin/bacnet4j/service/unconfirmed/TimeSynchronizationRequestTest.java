package com.serotonin.bacnet4j.service.unconfirmed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.DateTime;

public class TimeSynchronizationRequestTest {
    @Test
    public void timeSync() throws Exception {
        final LocalDevice ld1 = new LocalDevice(1, new DefaultTransport(new TestNetwork(1, 0)));
        final LocalDevice ld2 = new LocalDevice(2, new DefaultTransport(new TestNetwork(2, 0)));
        ld1.initialize();
        ld2.initialize();

        final DateTime dateTime = new DateTime();

        // Create the listener in device 2
        final AtomicReference<Address> receivedAddress = new AtomicReference<>(null);
        final AtomicReference<DateTime> receivedDateTime = new AtomicReference<>(null);
        final AtomicBoolean receivedUtc = new AtomicBoolean(false);
        ld2.getEventHandler().addListener(new DeviceEventAdapter() {
            @Override
            public void synchronizeTime(final Address from, final DateTime dateTime, final boolean utc) {
                receivedAddress.set(from);
                receivedDateTime.set(dateTime);
                receivedUtc.set(utc);
                synchronized (dateTime) {
                    dateTime.notify();
                }
            }
        });

        final RemoteDevice rd2 = ld1.getRemoteDevice(2).get();

        synchronized (dateTime) {
            ld1.send(rd2, new TimeSynchronizationRequest(dateTime));
            dateTime.wait(1000);
        }

        assertEquals(ld1.getAllLocalAddresses()[0], receivedAddress.get());
        assertEquals(dateTime, receivedDateTime.get());
        assertEquals(false, receivedUtc.get());
    }

    @Test
    public void disabledTimeSync() throws Exception {
        final LocalDevice ld1 = new LocalDevice(1, new DefaultTransport(new TestNetwork(1, 0)));
        final LocalDevice ld2 = new LocalDevice(2, new DefaultTransport(new TestNetwork(2, 0)));
        ld1.initialize();
        ld2.getServicesSupported().setTimeSynchronization(false);
        ld2.initialize();

        final DateTime dateTime = new DateTime();

        // Create the listener in device 2
        final AtomicReference<Address> receivedAddress = new AtomicReference<>(null);
        final AtomicReference<DateTime> receivedDateTime = new AtomicReference<>(null);
        final AtomicBoolean receivedUtc = new AtomicBoolean(false);
        ld2.getEventHandler().addListener(new DeviceEventAdapter() {
            @Override
            public void synchronizeTime(final Address from, final DateTime dateTime, final boolean utc) {
                receivedAddress.set(from);
                receivedDateTime.set(dateTime);
                receivedUtc.set(utc);
                synchronized (dateTime) {
                    dateTime.notify();
                }
            }
        });

        final RemoteDevice rd2 = ld1.getRemoteDevice(2).get();

        synchronized (dateTime) {
            ld1.send(rd2, new TimeSynchronizationRequest(dateTime));
            dateTime.wait(1000);
        }

        assertNull(receivedAddress.get());
        assertNull(receivedDateTime.get());
        assertFalse(receivedUtc.get());
    }
}
