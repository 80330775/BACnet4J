package com.serotonin.bacnet4j.obj;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.Destination;
import com.serotonin.bacnet4j.type.constructed.DeviceObjectPropertyReference;
import com.serotonin.bacnet4j.type.constructed.EventTransitionBits;
import com.serotonin.bacnet4j.type.constructed.FaultParameter;
import com.serotonin.bacnet4j.type.constructed.FaultParameter.FaultOutOfRange;
import com.serotonin.bacnet4j.type.constructed.FaultParameter.FaultOutOfRange.FaultNormalValue;
import com.serotonin.bacnet4j.type.constructed.PropertyStates;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.Recipient;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.StatusFlags;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EngineeringUnits;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Reliability;
import com.serotonin.bacnet4j.type.eventParameter.ChangeOfState;
import com.serotonin.bacnet4j.type.eventParameter.EventParameter;
import com.serotonin.bacnet4j.type.eventParameter.OutOfRange;
import com.serotonin.bacnet4j.type.notificationParameters.ChangeOfReliabilityNotif;
import com.serotonin.bacnet4j.type.notificationParameters.ChangeOfStateNotif;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class EventEnrollmentTest extends AbstractTest {
    private AnalogValueObject av0;
    private AnalogValueObject av1;
    private NotificationClassObject nc;

    @Override
    public void before() throws Exception {
        av0 = new AnalogValueObject(d3, 0, "av0", 0, EngineeringUnits.noUnits, false);
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.noFaultDetected);

        av1 = new AnalogValueObject(d3, 1, "av1", 0, EngineeringUnits.noUnits, false);
        av1.writePropertyInternal(PropertyIdentifier.minPresValue, new Real(50));

        nc = new NotificationClassObject(d1, 5, "nc5", 100, 5, 200, new EventTransitionBits(false, false, false));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void algorithmicReportingNoFault() throws Exception {
        final DeviceObjectPropertyReference ref = new DeviceObjectPropertyReference(
                new ObjectIdentifier(ObjectType.analogValue, 0), PropertyIdentifier.reliability, null,
                new ObjectIdentifier(ObjectType.device, 3));
        final SequenceOf<PropertyStates> alarmValues = new SequenceOf<>( //
                new PropertyStates(Reliability.activationFailure), //
                new PropertyStates(Reliability.communicationFailure), //
                new PropertyStates(Reliability.configurationError));
        final EventEnrollmentObject ee = new EventEnrollmentObject(d1, 0, "ee0", ref, NotifyType.event,
                new EventParameter(new ChangeOfState(new UnsignedInteger(1), alarmValues)),
                new EventTransitionBits(true, true, true), 5, 100, null, null);

        final SequenceOf<Destination> recipients = nc.get(PropertyIdentifier.recipientList);
        recipients.add(new Destination(new Recipient(rd2.getAddress()), new UnsignedInteger(10), new Boolean(true),
                new EventTransitionBits(true, true, true)));

        // Create an event listener on d2 to catch the event notifications.
        final EventNotifListener listener = new EventNotifListener();
        d2.getEventHandler().addListener(listener);

        // Ensure that initializing the event enrollment object didn't fire any notifications.
        assertEquals(0, listener.notifs.size());

        // Write a different normal value.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.lampFailure);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        Thread.sleep(1100);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        // Ensure that no notifications are sent.
        assertEquals(0, listener.notifs.size());

        // Set an offnormal value and then set back to normal before the time delay.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.activationFailure);
        Thread.sleep(500);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.noFaultDetected);
        Thread.sleep(600);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.

        // Do a real state change. Write an offnormal value. After 1s the alarm will be raised.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.communicationFailure);
        Thread.sleep(500);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        Thread.sleep(700);
        assertEquals(EventState.offnormal, ee.getProperty(PropertyIdentifier.eventState));
        assertEquals(new StatusFlags(true, false, false, false), ee.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        Map<String, Object> notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ee.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ee.getProperty(PropertyIdentifier.eventTimeStamps))
                .get(EventState.offnormal.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(5), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(100), notif.get("priority"));
        assertEquals(EventType.changeOfState, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.event, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.normal, notif.get("fromState"));
        assertEquals(EventState.offnormal, notif.get("toState"));
        assertEquals(
                new NotificationParameters(new ChangeOfStateNotif(new PropertyStates(Reliability.communicationFailure),
                        new StatusFlags(false, true, false, false))),
                notif.get("eventValues"));

        // Set to a different offnormal value. Ensure that no notification is send, because condition (3) in 13.3.2
        // is not supported.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.configurationError);
        Thread.sleep(500);
        assertEquals(0, listener.notifs.size());
        Thread.sleep(700);
        assertEquals(0, listener.notifs.size());

        // Set a normal value and then set back to offnormal before the time delay.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.overRange);
        Thread.sleep(500);
        assertEquals(EventState.offnormal, ee.getProperty(PropertyIdentifier.eventState)); // Still offnormal at this point.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.activationFailure);
        Thread.sleep(600);
        assertEquals(EventState.offnormal, ee.getProperty(PropertyIdentifier.eventState)); // Still offnormal at this point.

        // Do a real state change. Write a normal value. After 1s the notification will be sent.
        av0.writePropertyInternal(PropertyIdentifier.reliability, Reliability.tripped);
        Thread.sleep(500);
        assertEquals(EventState.offnormal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        Thread.sleep(700);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState));
        assertEquals(new StatusFlags(false, false, false, false), ee.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ee.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ee.getProperty(PropertyIdentifier.eventTimeStamps))
                .get(EventState.normal.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(5), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(200), notif.get("priority"));
        assertEquals(EventType.changeOfState, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.event, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.offnormal, notif.get("fromState"));
        assertEquals(EventState.normal, notif.get("toState"));
        assertEquals(new NotificationParameters(new ChangeOfStateNotif(new PropertyStates(Reliability.tripped),
                new StatusFlags(false, true, false, false))), notif.get("eventValues"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void algorithmicReportingWithFault() throws Exception {
        final DeviceObjectPropertyReference ref = new DeviceObjectPropertyReference(
                new ObjectIdentifier(ObjectType.analogValue, 1), PropertyIdentifier.minPresValue, null,
                new ObjectIdentifier(ObjectType.device, 3));
        final EventEnrollmentObject ee = new EventEnrollmentObject(d1, 0, "ee0", ref, NotifyType.alarm,
                new EventParameter(new OutOfRange(new UnsignedInteger(1), new Real(30), new Real(70), new Real(0))),
                new EventTransitionBits(true, true, true), 5, 100, null, new FaultParameter(
                        new FaultOutOfRange(new FaultNormalValue(new Real(10)), new FaultNormalValue(new Real(90)))));

        final SequenceOf<Destination> recipients = nc.get(PropertyIdentifier.recipientList);
        recipients.add(new Destination(new Recipient(rd2.getAddress()), new UnsignedInteger(10), new Boolean(true),
                new EventTransitionBits(true, true, true)));

        // Create an event listener on d2 to catch the event notifications.
        final EventNotifListener listener = new EventNotifListener();
        d2.getEventHandler().addListener(listener);

        // Ensure that initializing the event enrollment object didn't fire any notifications.
        assertEquals(0, listener.notifs.size());

        // Write a different normal value.
        av1.writePropertyInternal(PropertyIdentifier.minPresValue, new Real(45));
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        Thread.sleep(100);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState)); // Still normal at this point.
        assertEquals(Reliability.noFaultDetected, ee.getProperty(PropertyIdentifier.reliability));
        assertEquals(new StatusFlags(false, false, false, false), ee.getProperty(PropertyIdentifier.statusFlags));
        // Ensure that no notifications are sent.
        assertEquals(0, listener.notifs.size());

        // Write a fault value. Alarm will be sent.
        av1.writePropertyInternal(PropertyIdentifier.minPresValue, new Real(5));
        Thread.sleep(100);
        assertEquals(EventState.fault, ee.getProperty(PropertyIdentifier.eventState));
        assertEquals(Reliability.underRange, ee.getProperty(PropertyIdentifier.reliability));
        assertEquals(new StatusFlags(true, true, false, false), ee.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        Map<String, Object> notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ee.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ee.getProperty(PropertyIdentifier.eventTimeStamps))
                .get(EventState.fault.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(5), notif.get("notificationClass"));
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
                                new SequenceOf<>(new PropertyValue(PropertyIdentifier.objectPropertyReference, ref),
                                        new PropertyValue(PropertyIdentifier.minPresValue, new Real(5)),
                                        //new PropertyValue(PropertyIdentifier.reliability, Reliability.underRange),
                                        new PropertyValue(PropertyIdentifier.statusFlags,
                                                new StatusFlags(false, false, false, false))))),
                notif.get("eventValues"));

        // Write a different value. Another notification will be sent.
        av1.writePropertyInternal(PropertyIdentifier.minPresValue, new Real(95));
        Thread.sleep(100);
        assertEquals(EventState.fault, ee.getProperty(PropertyIdentifier.eventState));
        assertEquals(Reliability.overRange, ee.getProperty(PropertyIdentifier.reliability));
        assertEquals(new StatusFlags(true, true, false, false), ee.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ee.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ee.getProperty(PropertyIdentifier.eventTimeStamps))
                .get(EventState.fault.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(5), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(5), notif.get("priority"));
        assertEquals(EventType.changeOfReliability, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.alarm, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.fault, notif.get("fromState"));
        assertEquals(EventState.fault, notif.get("toState"));
        assertEquals(
                new NotificationParameters(
                        new ChangeOfReliabilityNotif(Reliability.overRange, new StatusFlags(true, true, false, false),
                                new SequenceOf<>(new PropertyValue(PropertyIdentifier.objectPropertyReference, ref),
                                        new PropertyValue(PropertyIdentifier.minPresValue, new Real(95)),
                                        //new PropertyValue(PropertyIdentifier.reliability, Reliability.overRange),
                                        new PropertyValue(PropertyIdentifier.statusFlags,
                                                new StatusFlags(false, false, false, false))))),
                notif.get("eventValues"));

        // Write a normal value. Another notification will be sent.
        av1.writePropertyInternal(PropertyIdentifier.minPresValue, new Real(55));
        Thread.sleep(100);
        assertEquals(EventState.normal, ee.getProperty(PropertyIdentifier.eventState));
        assertEquals(Reliability.noFaultDetected, ee.getProperty(PropertyIdentifier.reliability));
        assertEquals(new StatusFlags(false, false, false, false), ee.getProperty(PropertyIdentifier.statusFlags));

        // Ensure that a proper looking event notification was received.
        assertEquals(1, listener.notifs.size());
        notif = listener.notifs.remove(0);
        assertEquals(new UnsignedInteger(10), notif.get("processIdentifier"));
        assertEquals(rd1.getObjectIdentifier(), notif.get("initiatingDevice"));
        assertEquals(ee.getId(), notif.get("eventObjectIdentifier"));
        assertEquals(((BACnetArray<TimeStamp>) ee.getProperty(PropertyIdentifier.eventTimeStamps))
                .get(EventState.normal.getTransitionIndex()), notif.get("timeStamp"));
        assertEquals(new UnsignedInteger(5), notif.get("notificationClass"));
        assertEquals(new UnsignedInteger(200), notif.get("priority"));
        assertEquals(EventType.changeOfReliability, notif.get("eventType"));
        assertEquals(null, notif.get("messageText"));
        assertEquals(NotifyType.alarm, notif.get("notifyType"));
        assertEquals(new Boolean(false), notif.get("ackRequired"));
        assertEquals(EventState.fault, notif.get("fromState"));
        assertEquals(EventState.normal, notif.get("toState"));
        assertEquals(
                new NotificationParameters(
                        new ChangeOfReliabilityNotif(Reliability.noFaultDetected,
                                new StatusFlags(false, false, false, false),
                                new SequenceOf<>(new PropertyValue(PropertyIdentifier.objectPropertyReference, ref),
                                        new PropertyValue(PropertyIdentifier.minPresValue, new Real(55)),
                                        //new PropertyValue(PropertyIdentifier.reliability, Reliability.overRange),
                                        new PropertyValue(PropertyIdentifier.statusFlags,
                                                new StatusFlags(false, false, false, false))))),
                notif.get("eventValues"));
    }
}
