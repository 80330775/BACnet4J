package com.serotonin.bacnet4j.service.confirmed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.TestUtils;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetTimeoutException;
import com.serotonin.bacnet4j.exception.CommunicationDisabledException;
import com.serotonin.bacnet4j.exception.ErrorAPDUException;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.npdu.test.TestNetworkMap;
import com.serotonin.bacnet4j.service.confirmed.DeviceCommunicationControlRequest.EnableDisable;
import com.serotonin.bacnet4j.service.confirmed.ReinitializeDeviceRequest.ReinitializedStateOfDevice;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

import lohbihler.warp.WarpClock;

/**
 * All tests modify the communication control in device d1.
 */
public class DeviceCommunicationControlRequestTest {
    private final TestNetworkMap map = new TestNetworkMap();
    private WarpClock clock;
    private LocalDevice d1;
    private LocalDevice d2;
    private RemoteDevice rd1;
    private RemoteDevice rd2;

    @Before
    public void before() throws Exception {
        clock = new WarpClock();

        d1 = new LocalDevice(1, new DefaultTransport(new TestNetwork(map, 1, 0).withTimeout(200)));
        d1.setClock(clock);
        d1.initialize();

        d2 = new LocalDevice(2, new DefaultTransport(new TestNetwork(map, 2, 0).withTimeout(200))).initialize();

        rd1 = d2.getRemoteDeviceBlocking(1);
        rd2 = d1.getRemoteDeviceBlocking(2);
    }

    @After
    public void after() {
        d1.terminate();
        d2.terminate();
    }

    /**
     * Ensure that requests can be sent and responded when enabled by default
     */
    @Test
    public void communicationEnabled() throws BACnetException {
        // Send a request.
        assertNull(d2.getProperty(PropertyIdentifier.description));
        d1.send(rd2, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 2),
                PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
        assertEquals(new CharacterString("a"), d2.getProperty(PropertyIdentifier.description));

        // Receive a request.
        assertNull(d1.getProperty(PropertyIdentifier.description));
        d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
        assertEquals(new CharacterString("a"), d1.getProperty(PropertyIdentifier.description));
    }

    /**
     * Ensure that requests cannot be sent - except IAm, DCCR and reinitialize - when disable initiation, and that
     * responses can still be received and responded.
     */
    @Test
    public void disableInitiation() throws Exception {
        // Disable initiation
        d2.send(rd1, new DeviceCommunicationControlRequest(null, EnableDisable.disableInitiation, null)).get();

        // Fail to send a request.
        try {
            d1.send(rd2, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 2),
                    PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
            fail("CommunicationDisabledException should have been thrown");
        } catch (@SuppressWarnings("unused") final CommunicationDisabledException e) {
            // Expected
        }

        // Receive a request
        assertNull(d1.getProperty(PropertyIdentifier.description));
        d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
        assertEquals(new CharacterString("a"), d1.getProperty(PropertyIdentifier.description));

        // Sending of IAms...
        final AtomicInteger iamCount = new AtomicInteger(0);
        d2.getEventHandler().addListener(new DeviceEventAdapter() {
            @Override
            public void iAmReceived(final RemoteDevice d) {
                iamCount.incrementAndGet();
            }
        });

        // Should also fail to send an IAm
        d1.send(rd2, d1.getIAm());
        Thread.sleep(100);
        assertEquals(0, iamCount.get());

        // But should still respond to a WhoIs
        d2.send(rd1, new WhoIsRequest(1, 1));
        Thread.sleep(100);
        assertEquals(1, iamCount.get());

        // Re-enable
        d2.send(rd1, new DeviceCommunicationControlRequest(null, EnableDisable.enable, null)).get();

        // Send a request. This time it succeeds.
        assertNull(d2.getProperty(PropertyIdentifier.description));
        d1.send(rd2, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 2),
                PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
        assertEquals(new CharacterString("a"), d2.getProperty(PropertyIdentifier.description));
    }

    /**
     * Ensure that only DCCR and reinitialize are handled when disabled
     */
    @Test
    public void disable() throws BACnetException {
        // Disable
        d2.send(rd1, new DeviceCommunicationControlRequest(null, EnableDisable.disable, null)).get();

        // Fail to send a request.
        try {
            d1.send(rd2, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 2),
                    PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
            fail("CommunicationDisabledException should have been thrown");
        } catch (@SuppressWarnings("unused") final CommunicationDisabledException e) {
            // Expected
        }

        // Fail to receive a request
        try {
            d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                    PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
            fail("BACnetTimeoutException should have been thrown");
        } catch (@SuppressWarnings("unused") final BACnetTimeoutException e) {
            // Expected
        }

        // Reinitialize "works", or at least doesn't return an error.
        d2.send(rd1, new ReinitializeDeviceRequest(ReinitializedStateOfDevice.activateChanges, null)).get();

        // Re-enable
        d2.send(rd1, new DeviceCommunicationControlRequest(null, EnableDisable.enable, null)).get();

        // Send a request. This time it succeeds.
        assertNull(d2.getProperty(PropertyIdentifier.description));
        d1.send(rd2, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 2),
                PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
        assertEquals(new CharacterString("a"), d2.getProperty(PropertyIdentifier.description));

        // Receive a request. This time it too succeeds. Note that the value is already "a", because requests are
        // still processed, just not responded.
        assertEquals(new CharacterString("a"), d1.getProperty(PropertyIdentifier.description));
        d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                PropertyIdentifier.description, null, new CharacterString("b"), null)).get();
        assertEquals(new CharacterString("b"), d1.getProperty(PropertyIdentifier.description));
    }

    /**
     * Ensure that the timer works.
     */
    @Test
    public void timer() throws BACnetException {
        // Disable for 5 minutes.
        d2.send(rd1, new DeviceCommunicationControlRequest(new UnsignedInteger(5), EnableDisable.disable, null)).get();

        // Fail to receive a request
        try {
            d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                    PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
            fail("BACnetTimeoutException should have been thrown");
        } catch (@SuppressWarnings("unused") final BACnetTimeoutException e) {
            // Expected
        }

        // Let the 5 minutes elapse.
        clock.plusMinutes(6);

        // Receive a request. This time it too succeeds. Note that the value is already "a", because requests are
        // still processed, just not responded.
        assertEquals(new CharacterString("a"), d1.getProperty(PropertyIdentifier.description));
        d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                PropertyIdentifier.description, null, new CharacterString("b"), null)).get();
        assertEquals(new CharacterString("b"), d1.getProperty(PropertyIdentifier.description));
    }

    /**
     * Ensure that the timer gets cancelled.
     */
    @Test
    public void timerCancel() throws BACnetException {
        // Disable for 5 minutes.
        d2.send(rd1, new DeviceCommunicationControlRequest(new UnsignedInteger(5), EnableDisable.disable, null)).get();

        // Fail to receive a request
        try {
            d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                    PropertyIdentifier.description, null, new CharacterString("a"), null)).get();
            fail("BACnetTimeoutException should have been thrown");
        } catch (@SuppressWarnings("unused") final BACnetTimeoutException e) {
            // Expected
        }

        // Let 1 minute go by.
        clock.plusMinutes(1);

        // Re-enable. Yes, the timeout is still there, which shouldn't matter.
        d2.send(rd1, new DeviceCommunicationControlRequest(new UnsignedInteger(5), EnableDisable.enable, null)).get();

        // Receive a request. This time it too succeeds. Note that the value is already "a", because requests are
        // still processed, just not responded.
        assertEquals(new CharacterString("a"), d1.getProperty(PropertyIdentifier.description));
        d2.send(rd1, new WritePropertyRequest(new ObjectIdentifier(ObjectType.device, 1),
                PropertyIdentifier.description, null, new CharacterString("b"), null)).get();
        assertEquals(new CharacterString("b"), d1.getProperty(PropertyIdentifier.description));
    }

    /**
     * Ensure that the password functionality works.
     */
    @Test
    public void password() throws BACnetException {
        d1.setPassword("asdf");

        // Try to disable with null
        try {
            d2.send(rd1, new DeviceCommunicationControlRequest(null, EnableDisable.disable, null)).get();
            fail("ErrorAPDUException should have been thrown");
        } catch (final ErrorAPDUException e) {
            TestUtils.assertErrorClassAndCode(e.getError(), ErrorClass.security, ErrorCode.passwordFailure);
        }

        // Try to disable with incorrect password
        try {
            d2.send(rd1,
                    new DeviceCommunicationControlRequest(null, EnableDisable.disable, new CharacterString("qwer")))
                    .get();
            fail("ErrorAPDUException should have been thrown");
        } catch (final ErrorAPDUException e) {
            TestUtils.assertErrorClassAndCode(e.getError(), ErrorClass.security, ErrorCode.passwordFailure);
        }

        // Try to disable with correct password
        d2.send(rd1, new DeviceCommunicationControlRequest(null, EnableDisable.disable, new CharacterString("asdf")))
                .get();
    }
}
