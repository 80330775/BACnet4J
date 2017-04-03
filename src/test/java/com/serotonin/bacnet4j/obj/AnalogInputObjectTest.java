package com.serotonin.bacnet4j.obj;

import static com.serotonin.bacnet4j.TestUtils.assertBACnetServiceException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.npdu.test.TestNetworkMap;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.Destination;
import com.serotonin.bacnet4j.type.constructed.EventTransitionBits;
import com.serotonin.bacnet4j.type.constructed.LimitEnable;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.Recipient;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.StatusFlags;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Reliability;
import com.serotonin.bacnet4j.type.notificationParameters.ChangeOfReliabilityNotif;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.notificationParameters.OutOfRangeNotif;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

import lohbihler.warp.WarpClock;

public class AnalogInputObjectTest {
    static final Logger LOG = LoggerFactory.getLogger(AnalogInputObjectTest.class);

    private final WarpClock clock = new WarpClock();
    private final TestNetworkMap map = new TestNetworkMap();
    private LocalDevice d1;
    private LocalDevice d2;
    private RemoteDevice rd1;
    private RemoteDevice rd2;
    private AnalogInputObject ai;
    private NotificationClassObject nc;

    @Before
    public void abstractBefore() throws Exception {
        d1 = new LocalDevice(1, new DefaultTransport(new TestNetwork(map, 1, 0))).withClock(clock).initialize();
        d2 = new LocalDevice(2, new DefaultTransport(new TestNetwork(map, 2, 0))).withClock(clock).initialize();

        rd1 = d2.getRemoteDevice(1).get();
        rd2 = d1.getRemoteDevice(2).get();

        ai = new AnalogInputObject(d1, 0, "ai0", 50, EngineeringUnits.amperes, false);

        nc = new NotificationClassObject(d1, 17, "nc17", 100, 5, 200, new EventTransitionBits(false, false, false));
    }

    @After
    public void abstractAfter() {
        d1.terminate();
        d2.terminate();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void intrinsicReporting() throws Exception {
        final SequenceOf<Destination> recipients = nc.get(PropertyIdentifier.recipientList);
        recipients.add(new Destination(new Recipient(rd2.getAddress()), new UnsignedInteger(10), new Boolean(true),
                new EventTransitionBits(true, true, true)));

        // Create an event listener on d2 to catch the event notifications.
        final EventNotifListener listener = new EventNotifListener();
        d2.getEventHandler().addListener(listener);

        ai.supportIntrinsicReporting(1, 17, 100, 20, 5, 120, 0, new LimitEnable(true, true),
                new EventTransitionBits(true, true, true), NotifyType.alarm, 2);
        // Ensure that initializing the intrinsic reporting didn't fire any notifications.
        assertEquals(0, listener.notifs.size());

        // Write a different normal value.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(60));
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        clock.plus(1100, TimeUnit.MILLISECONDS, 1100, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        // Ensure that no notifications are sent.
        assertEquals(0, listener.notifs.size());

        // Set an out of range value and then set back to normal before the time delay.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(110));
        clock.plus(500, TimeUnit.MILLISECONDS, 500, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(90));
        clock.plus(600, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.

        // Do a real state change. Write an out of range value. After 1 second the alarm will be raised.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(10));
        clock.plus(500, TimeUnit.MILLISECONDS, 500, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        clock.plus(600, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.lowLimit, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(new StatusFlags(true, false, false, false), ai.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        Map<String, Object> notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ai.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ai.getProperty(PropertyIdentifier.eventTimeStamps))
                .getBase1(EventState.offnormal.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(17), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(100), notif.get("priority"));
        assertEquals(EventType.outOfRange, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.alarm, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.normal, notif.get("fromState"));
        assertEquals(EventState.lowLimit, notif.get("toState"));
        assertEquals(new NotificationParameters(new OutOfRangeNotif(new Real(10),
                new StatusFlags(true, false, false, false), new Real(5), new Real(20))), notif.get("eventValues"));

        // Disable low limit checking. Will return to normal immediately.
        ai.writePropertyInternal(PropertyIdentifier.limitEnable, new LimitEnable(false, true));
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState));
        Thread.sleep(100);
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ai.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ai.getProperty(PropertyIdentifier.eventTimeStamps))
                .getBase1(EventState.normal.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(17), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(200), notif.get("priority"));
        assertEquals(EventType.outOfRange, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.alarm, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.lowLimit, notif.get("fromState"));
        assertEquals(EventState.normal, notif.get("toState"));
        assertEquals(
                new NotificationParameters(new OutOfRangeNotif(new Real(10),
                        new StatusFlags(false, false, false, false), new Real(5), new Real(20))),
                notif.get("eventValues"));

        // Re-enable low limit checking. Will return to low-limit after 1 second.
        ai.writePropertyInternal(PropertyIdentifier.limitEnable, new LimitEnable(true, true));
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState));
        clock.plus(1100, TimeUnit.MILLISECONDS, 1100, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.lowLimit, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(EventType.outOfRange, notif.get("eventType"));
        assertEquals(EventState.normal, notif.get("fromState"));
        assertEquals(EventState.lowLimit, notif.get("toState"));
        assertEquals(new NotificationParameters(new OutOfRangeNotif(new Real(10),
                new StatusFlags(true, false, false, false), new Real(5), new Real(20))), notif.get("eventValues"));

        // Go to a high limit. Will change to high-limit after 1 second.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(110));
        assertEquals(EventState.lowLimit, ai.getProperty(PropertyIdentifier.eventState));
        clock.plus(1100, TimeUnit.MILLISECONDS, 1100, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.highLimit, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(EventState.lowLimit, notif.get("fromState"));
        assertEquals(EventState.highLimit, notif.get("toState"));
        assertEquals(
                new NotificationParameters(new OutOfRangeNotif(new Real(110),
                        new StatusFlags(true, false, false, false), new Real(5), new Real(100))),
                notif.get("eventValues"));

        // Reduce to within the deadband. No notification.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(95));
        assertEquals(EventState.highLimit, ai.getProperty(PropertyIdentifier.eventState));
        clock.plus(1100, TimeUnit.MILLISECONDS, 1100, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.highLimit, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(0, listener.notifs.size());

        // Reduce to below the deadband. Return to normal after 2 seconds.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(94));
        assertEquals(EventState.highLimit, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(0, listener.notifs.size());
        clock.plus(1500, TimeUnit.MILLISECONDS, 1500, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.highLimit, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(0, listener.notifs.size());
        clock.plus(600, TimeUnit.MILLISECONDS, 600, TimeUnit.MILLISECONDS, 0, 40);
        assertEquals(EventState.normal, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(EventState.highLimit, notif.get("fromState"));
        assertEquals(EventState.normal, notif.get("toState"));
        assertEquals(
                new NotificationParameters(new OutOfRangeNotif(new Real(94),
                        new StatusFlags(false, false, false, false), new Real(5), new Real(100))),
                notif.get("eventValues"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void fault() throws Exception {
        final SequenceOf<Destination> recipients = nc.get(PropertyIdentifier.recipientList);
        recipients.add(new Destination(new Recipient(rd2.getAddress()), new UnsignedInteger(10), new Boolean(true),
                new EventTransitionBits(true, true, true)));

        // Create an event listener on d2 to catch the event notifications.
        final EventNotifListener listener = new EventNotifListener();
        d2.getEventHandler().addListener(listener);

        ai.supportIntrinsicReporting(1, 17, 100, 20, 5, 120, 0, new LimitEnable(true, true),
                new EventTransitionBits(true, true, true), NotifyType.alarm, 2);
        // Ensure that initializing the intrinsic reporting didn't fire any notifications.
        assertEquals(0, listener.notifs.size());

        // Write a fault value.
        ai.writePropertyInternal(PropertyIdentifier.presentValue, new Real(-5));
        Thread.sleep(40);
        assertEquals(EventState.fault, ai.getProperty(PropertyIdentifier.eventState));
        assertEquals(new StatusFlags(true, true, false, false), ai.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        final Map<String, Object> notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ai.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ai.getProperty(PropertyIdentifier.eventTimeStamps))
                .getBase1(EventState.fault.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(17), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(5), notif.get("priority"));
        assertEquals(EventType.changeOfReliability, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.alarm, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.normal, notif.get("fromState"));
        assertEquals(EventState.fault, notif.get("toState"));
        assertEquals(
                new NotificationParameters(
                        new ChangeOfReliabilityNotif(Reliability.underRange, new StatusFlags(true, true, false, false),
                                new SequenceOf<>(new PropertyValue(PropertyIdentifier.presentValue, new Real(-5))))),
                notif.get("eventValues"));
    }

    @Test
    public void propertyConformanceRequired() throws Exception {
        assertNotNull(ai.getProperty(PropertyIdentifier.objectIdentifier));
        assertNotNull(ai.getProperty(PropertyIdentifier.objectName));
        assertNotNull(ai.getProperty(PropertyIdentifier.objectType));
        assertNotNull(ai.getProperty(PropertyIdentifier.presentValue));
        assertNotNull(ai.getProperty(PropertyIdentifier.statusFlags));
        assertNotNull(ai.getProperty(PropertyIdentifier.eventState));
        assertNotNull(ai.getProperty(PropertyIdentifier.outOfService));
        assertNotNull(ai.getProperty(PropertyIdentifier.units));
        assertNotNull(ai.getProperty(PropertyIdentifier.propertyList));
    }

    @Test
    public void propertyConformanceEditableWhenOutOfService() throws BACnetServiceException {
        // Should not be writable while in service
        assertBACnetServiceException(
                () -> ai.writeProperty(null,
                        new PropertyValue(PropertyIdentifier.presentValue, null, new Real(51), null)),
                ErrorClass.property, ErrorCode.writeAccessDenied);

        // Should be writable while out of service.
        ai.writeProperty(null, PropertyIdentifier.outOfService, new Boolean(true));
        ai.writeProperty(null, new PropertyValue(PropertyIdentifier.presentValue, null, new Real(51), null));
    }

    @Test
    public void propertyConformanceReadOnly() {
        assertBACnetServiceException(
                () -> ai.writeProperty(null,
                        new PropertyValue(PropertyIdentifier.eventMessageTexts, new UnsignedInteger(2),
                                new CharacterString("should fail"), null)),
                ErrorClass.property, ErrorCode.writeAccessDenied);
    }

    @Test
    public void propertyConformanceRequiredWhenCOVReporting() throws Exception {
        ai.supportCovReporting(1);
        assertNotNull(ai.getProperty(PropertyIdentifier.covIncrement));
    }

    @Test
    public void propertyConformanceRequiredWhenIntrinsicReporting() throws Exception {
        ai.supportIntrinsicReporting(30, 17, 60, 40, 1, 70, 30, new LimitEnable(true, true),
                new EventTransitionBits(true, true, true), NotifyType.alarm, 10);
        assertNotNull(ai.getProperty(PropertyIdentifier.timeDelay));
        assertNotNull(ai.getProperty(PropertyIdentifier.notificationClass));
        assertNotNull(ai.getProperty(PropertyIdentifier.highLimit));
        assertNotNull(ai.getProperty(PropertyIdentifier.lowLimit));
        assertNotNull(ai.getProperty(PropertyIdentifier.deadband));
        assertNotNull(ai.getProperty(PropertyIdentifier.faultHighLimit));
        assertNotNull(ai.getProperty(PropertyIdentifier.faultLowLimit));
        assertNotNull(ai.getProperty(PropertyIdentifier.limitEnable));
        assertNotNull(ai.getProperty(PropertyIdentifier.eventEnable));
        assertNotNull(ai.getProperty(PropertyIdentifier.ackedTransitions));
        assertNotNull(ai.getProperty(PropertyIdentifier.notifyType));
        assertNotNull(ai.getProperty(PropertyIdentifier.eventTimeStamps));
        assertNotNull(ai.getProperty(PropertyIdentifier.eventDetectionEnable));
    }

    @Test
    public void propertyConformanceForbiddenWhenNotIntrinsicReporting() throws Exception {
        assertNull(ai.getProperty(PropertyIdentifier.timeDelay));
        assertNull(ai.getProperty(PropertyIdentifier.notificationClass));
        assertNull(ai.getProperty(PropertyIdentifier.highLimit));
        assertNull(ai.getProperty(PropertyIdentifier.lowLimit));
        assertNull(ai.getProperty(PropertyIdentifier.deadband));
        assertNull(ai.getProperty(PropertyIdentifier.faultHighLimit));
        assertNull(ai.getProperty(PropertyIdentifier.faultLowLimit));
        assertNull(ai.getProperty(PropertyIdentifier.limitEnable));
        assertNull(ai.getProperty(PropertyIdentifier.eventEnable));
        assertNull(ai.getProperty(PropertyIdentifier.ackedTransitions));
        assertNull(ai.getProperty(PropertyIdentifier.notifyType));
        assertNull(ai.getProperty(PropertyIdentifier.eventTimeStamps));
        assertNull(ai.getProperty(PropertyIdentifier.eventMessageTexts));
        assertNull(ai.getProperty(PropertyIdentifier.eventMessageTextsConfig));
        assertNull(ai.getProperty(PropertyIdentifier.eventDetectionEnable));
        assertNull(ai.getProperty(PropertyIdentifier.eventAlgorithmInhibitRef));
        assertNull(ai.getProperty(PropertyIdentifier.eventAlgorithmInhibit));
        assertNull(ai.getProperty(PropertyIdentifier.timeDelayNormal));
    }
}
