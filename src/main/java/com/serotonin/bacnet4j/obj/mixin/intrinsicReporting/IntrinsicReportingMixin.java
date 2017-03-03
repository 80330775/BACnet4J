/*
 * ============================================================================
 * GNU General Public License
 * ============================================================================
 *
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * When signing a commercial license with Infinite Automation Software,
 * the following extension to GPL is made. A special exception to the GPL is
 * included to allow you to distribute a combined work that includes BAcnet4J
 * without being obliged to provide the source code for any proprietary components.
 *
 * See www.infiniteautomation.com for commercial license options.
 *
 * @author Matthew Lohbihler
 */
package com.serotonin.bacnet4j.obj.mixin.intrinsicReporting;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.AbstractMixin;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.obj.NotificationClassObject;
import com.serotonin.bacnet4j.service.acknowledgement.GetAlarmSummaryAck.AlarmSummary;
import com.serotonin.bacnet4j.service.acknowledgement.GetEnrollmentSummaryAck.EnrollmentSummary;
import com.serotonin.bacnet4j.service.acknowledgement.GetEventInformationAck.EventSummary;
import com.serotonin.bacnet4j.service.confirmed.ConfirmedEventNotificationRequest;
import com.serotonin.bacnet4j.service.confirmed.GetEnrollmentSummaryRequest.AcknowledgmentFilter;
import com.serotonin.bacnet4j.service.confirmed.GetEnrollmentSummaryRequest.EventStateFilter;
import com.serotonin.bacnet4j.service.confirmed.GetEnrollmentSummaryRequest.PriorityFilter;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedEventNotificationRequest;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.Destination;
import com.serotonin.bacnet4j.type.constructed.EventTransitionBits;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.RecipientProcess;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.StatusFlags;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.enumerated.Reliability;
import com.serotonin.bacnet4j.type.notificationParameters.ChangeOfReliability;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

/**
 * Base class for intrinsic reporting.
 *
 * @author Matthew
 */
public class IntrinsicReportingMixin extends AbstractMixin {
    static final Logger LOG = LoggerFactory.getLogger(IntrinsicReportingMixin.class);

    // Configuration
    private final EventAlgorithm eventAlgo;
    private final FaultAlgorithm faultAlgo;
    private final PropertyIdentifier[] triggerProperties;
    // See table 13-5.
    private final PropertyIdentifier[] changeOfReliabilityProperties;

    // Runtime
    private Delayer delayTimer;
    private ScheduledFuture<?> delayTimerFuture;

    public IntrinsicReportingMixin(final BACnetObject bo, final EventAlgorithm eventAlgo,
            final FaultAlgorithm faultAlgo, final PropertyIdentifier[] triggerProperties,
            final PropertyIdentifier[] changeOfReliabilityProperties) {
        super(bo);

        this.eventAlgo = eventAlgo;
        this.faultAlgo = faultAlgo;
        this.triggerProperties = triggerProperties;
        this.changeOfReliabilityProperties = changeOfReliabilityProperties;

        // Check that the notification object with the given instance number exists.
        final UnsignedInteger ncId = get(PropertyIdentifier.notificationClass);
        final ObjectIdentifier ncOid = new ObjectIdentifier(ObjectType.notificationClass, ncId.intValue());
        if (getLocalDevice().getObject(ncOid) == null)
            throw new BACnetRuntimeException("Notification class with id " + ncId + " does not exist");

        // Defaulted properties
        writePropertyInternal(PropertyIdentifier.ackedTransitions, new EventTransitionBits(true, true, true));
        writePropertyInternal(PropertyIdentifier.eventTimeStamps, new BACnetArray<>(TimeStamp.UNSPECIFIED_DATETIME,
                TimeStamp.UNSPECIFIED_DATETIME, TimeStamp.UNSPECIFIED_DATETIME));
        writePropertyInternal(PropertyIdentifier.eventMessageTexts,
                new BACnetArray<>(CharacterString.EMPTY, CharacterString.EMPTY, CharacterString.EMPTY));
        writePropertyInternal(PropertyIdentifier.eventMessageTextsConfig,
                new BACnetArray<>(CharacterString.EMPTY, CharacterString.EMPTY, CharacterString.EMPTY));
        //writePropertyImpl(PropertyIdentifier.eventAlgorithmInhibitRef, new ObjectPropertyReference()); Not supported
        writePropertyInternal(PropertyIdentifier.eventAlgorithmInhibit, new Boolean(false));
        writePropertyInternal(PropertyIdentifier.eventDetectionEnable, new Boolean(true));
        writePropertyInternal(PropertyIdentifier.reliabilityEvaluationInhibit, new Boolean(false));

        // Update the state with the current values in the object.
        for (final PropertyIdentifier pid : triggerProperties)
            afterWriteProperty(pid, null, get(pid));
    }

    @Override
    protected boolean validateProperty(final PropertyValue value) throws BACnetServiceException {
        if (PropertyIdentifier.eventDetectionEnable.equals(value.getPropertyIdentifier()))
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);
        if (PropertyIdentifier.eventTimeStamps.equals(value.getPropertyIdentifier()))
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);
        if (PropertyIdentifier.eventMessageTexts.equals(value.getPropertyIdentifier()))
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);
        if (PropertyIdentifier.eventAlgorithmInhibitRef.equals(value.getPropertyIdentifier()))
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);

        return super.validateProperty(value);
    }

    @Override
    protected synchronized void afterWriteProperty(final PropertyIdentifier pid, final Encodable oldValue,
            final Encodable newValue) {
        if (PropertyIdentifier.reliability.equals(pid)) {
            // Is reliability evaluation inhibited?
            final Boolean rei = get(PropertyIdentifier.reliabilityEvaluationInhibit);
            if (rei == null || !rei.booleanValue()) {
                // Not inhibited. Check the reliability
                final Reliability reli = (Reliability) newValue;
                if (!reli.equals(Reliability.noFaultDetected))
                    // Fault detected. Do an immediate state change to fault.
                    doStateTransition(EventState.fault);
                else if (!newValue.equals(oldValue)) {
                    // No fault detected. Do an immediate state change to normal.
                    doStateTransition(EventState.normal);
                    // Now call the event algorithm in case we need a change to offnormal.
                    executeEventAlgo();
                }
            }
        } else if (pid.isOneOf(triggerProperties) /* && !newValue.equals(oldValue) */) {
            // Check if the value has changed to a fault value.
            boolean fault = false;
            if (faultAlgo != null) {
                final Reliability newReli = faultAlgo.evaluate(oldValue, newValue);
                if (newReli != null) {
                    // After setting this value there is nothing else that need be done since this method will be
                    // called again due to the property change, and the reliability code above will handle it.
                    writePropertyInternal(PropertyIdentifier.reliability, newReli);
                    fault = true;
                }
            }

            if (!fault) {
                // Ensure there is no current fault.
                final Reliability reli = get(PropertyIdentifier.reliability);
                if (reli == null || reli.equals(Reliability.noFaultDetected))
                    // No fault detected. Run the event algorithm
                    executeEventAlgo();
            }
        } else if (PropertyIdentifier.eventAlgorithmInhibit.equals(pid) && !newValue.equals(oldValue)) {
            // Ensure there is no fault.
            final Reliability reli = get(PropertyIdentifier.reliability);
            if (reli == null || reli.equals(Reliability.noFaultDetected)) {
                // No fault detected.
                final Boolean eai = (Boolean) newValue;
                if (eai.booleanValue())
                    // Inhibited. Update the event state immediately to normal.
                    doStateTransition(EventState.normal);
                else
                    // Uninhibited.
                    executeEventAlgo();
            }
        }
    }

    private void executeEventAlgo() {
        // Check if the event algorithm is inhibited.
        final Boolean eai = get(PropertyIdentifier.eventAlgorithmInhibit);
        if (eai == null || !eai.booleanValue()) {
            // Uninhibited. Continue with event detection. First determine the provisional event state.
            final StateTransition transition = eventAlgo.evaluateEventState();
            if (transition != null) {
                LOG.debug("Event algo indicated a change to event state {}", transition);
                updateEventState(transition);
            } else
                cancelTimer();
        }
    }

    static class StateTransition {
        final EventState toState;
        final UnsignedInteger delay;

        public StateTransition(final EventState toState, final UnsignedInteger delay) {
            this.toState = toState;
            this.delay = delay;
        }

        @Override
        public String toString() {
            return "StateTransition [toState=" + toState + ", delay=" + delay + "]";
        }
    }

    private void updateEventState(final StateTransition transition) {
        if (transition.delay == null)
            // Do an immediate state transition.
            doStateTransition(transition.toState);
        else {
            if (delayTimer != null && delayTimer.toState.equals(transition.toState))
                // There already is a timer for the same state transition. Ignore this one.
                return;
            if (delayTimer != null)
                // Cancel the existing timer
                delayTimerFuture.cancel(false);

            // Create a timer for the state.
            delayTimer = new Delayer(transition.toState);
            delayTimerFuture = getLocalDevice().schedule(delayTimer, transition.delay.intValue(), TimeUnit.SECONDS);
        }
    }

    void doStateTransition(final EventState toState) {
        final EventState fromState = get(PropertyIdentifier.eventState);
        LOG.debug("Event state changing from {} to {}", fromState, toState);

        // If there is a timer in effect, cancel it.
        cancelTimer();

        //
        // Perform the state change. 13.2.2.1.4
        //
        writePropertyInternal(PropertyIdentifier.eventState, toState);

        BACnetArray<TimeStamp> ets = get(PropertyIdentifier.eventTimeStamps);
        // Make a copy in which to make the change so that the write property method works properly.
        ets = new BACnetArray<>(ets);
        ets.set(toState.getTransitionIndex(), new TimeStamp(new DateTime()));
        writePropertyInternal(PropertyIdentifier.eventTimeStamps, ets);

        // Not implemented
        //BACnetArray<CharacterString> emt = get(PropertyIdentifier.eventMessageTexts);
        //emt.set(arrayIndex, new CharacterString(""));

        // Get the notification class object.
        final UnsignedInteger ncId = get(PropertyIdentifier.notificationClass);
        final BACnetObject nc = getLocalDevice()
                .getObject(new ObjectIdentifier(ObjectType.notificationClass, ncId.intValue()));

        //
        // Update acknowledged transitions. 13.2.3
        //
        EventTransitionBits ackedTransitions = get(PropertyIdentifier.ackedTransitions);
        final EventTransitionBits ackRequired = nc.get(PropertyIdentifier.ackRequired);

        // Make a copy in which to make the change so that the write property method works properly.
        ackedTransitions = new EventTransitionBits(ackedTransitions);

        // If the corresponding bit in Ack_Required is set then the bit in Acked_Transitions is
        // cleared, otherwise it is set.
        final boolean isAckRequired = ackRequired.contains(toState);
        ackedTransitions.setValue(toState.getTransitionIndex(), !isAckRequired);
        writePropertyInternal(PropertyIdentifier.ackedTransitions, ackedTransitions);

        //
        // Event notification distribution. 13.2.5
        //
        final EventTransitionBits eventEnable = get(PropertyIdentifier.eventEnable);

        // Do we need to send any notifications?
        if (eventEnable.contains(toState)) {
            // Send notifications for this transition.
            LOG.debug("Notification enabled for state change to {}. Checking recipient list", toState);

            final SequenceOf<Destination> recipientList = nc.get(PropertyIdentifier.recipientList);
            final NotifyType notifyType = get(PropertyIdentifier.notifyType);
            final BACnetArray<UnsignedInteger> priority = nc.get(PropertyIdentifier.priority);
            final TimeStamp now = new TimeStamp(new DateTime());

            EventType eventType;
            NotificationParameters eventValues;
            if (fromState.equals(EventState.fault) || toState.equals(EventState.fault)) {
                eventType = EventType.changeOfReliability;

                // Gather the property values required for the change of reliability notification.
                final SequenceOf<PropertyValue> propertyValues = new SequenceOf<>();
                for (final PropertyIdentifier pid : changeOfReliabilityProperties)
                    propertyValues.add(new PropertyValue(pid, get(pid)));

                eventValues = new NotificationParameters(new ChangeOfReliability( //
                        (Reliability) get(PropertyIdentifier.reliability), //
                        (StatusFlags) get(PropertyIdentifier.statusFlags), //
                        propertyValues));
            } else {
                eventType = eventAlgo.getEventType();
                eventValues = eventAlgo.getEventValues(fromState, toState);
            }

            sendNotifications(recipientList, now, nc, priority, toState, eventType, null, notifyType, //
                    new Boolean(isAckRequired), fromState, eventValues);
        }
    }

    private void cancelTimer() {
        if (delayTimer != null) {
            LOG.debug("Cancelling delay timer");
            delayTimerFuture.cancel(false);
            delayTimerFuture = null;
            delayTimer = null;
        }
    }

    class Delayer implements Runnable {
        final EventState toState;

        public Delayer(final EventState toState) {
            this.toState = toState;
        }

        @Override
        synchronized public void run() {
            delayTimerFuture = null;
            delayTimer = null;
            doStateTransition(toState);
        }
    }

    //
    //
    // Acknowledgements
    //
    public synchronized void acknowledgeAlarm(final UnsignedInteger acknowledgingProcessIdentifier,
            final EventState eventStateAcknowledged, final TimeStamp timeStamp,
            final CharacterString acknowledgmentSource, final TimeStamp timeOfAcknowledgment)
            throws BACnetServiceException {
        LOG.debug("Alarm acknowledgement received for {}, ts={}, tsAck={}", eventStateAcknowledged, timeStamp,
                timeOfAcknowledgment);
        // Verify that the timestamp for the given acknowledgement matches.
        final BACnetArray<TimeStamp> ets = get(PropertyIdentifier.eventTimeStamps);
        final TimeStamp ts = ets.get(eventStateAcknowledged.getTransitionIndex());
        if (!timeStamp.equals(ts))
            throw new BACnetServiceException(ErrorClass.services, ErrorCode.invalidTimeStamp);

        //
        // Update acknowledged transitions.
        //
        EventTransitionBits ackedTransitions = get(PropertyIdentifier.ackedTransitions);
        if (ackedTransitions.getValue(eventStateAcknowledged.getTransitionIndex())) {
            LOG.info("Aborting alarm acknowledgement for state that did not require acknowledgement");
            return;
        }

        // Make a copy in which to make the change so that the write property method works properly.
        ackedTransitions = new EventTransitionBits(ackedTransitions);
        ackedTransitions.setValue(eventStateAcknowledged.getTransitionIndex(), true);
        writePropertyInternal(PropertyIdentifier.ackedTransitions, ackedTransitions);

        //
        // Event notification distribution.
        //
        final EventTransitionBits eventEnable = get(PropertyIdentifier.eventEnable);

        // Get the notification class object.
        final UnsignedInteger ncId = get(PropertyIdentifier.notificationClass);
        final BACnetObject nc = getLocalDevice()
                .getObject(new ObjectIdentifier(ObjectType.notificationClass, ncId.intValue()));

        // Do we need to send any notifications?
        if (eventEnable.contains(eventStateAcknowledged)) {
            // Send notifications for this transition.
            LOG.debug("Notification enabled for ack of {}. Checking recipient list", eventStateAcknowledged);

            final StringBuilder sb = new StringBuilder();
            sb.append(acknowledgingProcessIdentifier);
            if (acknowledgmentSource != null)
                sb.append(": ").append(acknowledgmentSource.getValue());
            final CharacterString messageText = new CharacterString(sb.toString());

            final SequenceOf<Destination> recipientList = nc.get(PropertyIdentifier.recipientList);
            final BACnetArray<UnsignedInteger> priority = nc.get(PropertyIdentifier.priority);

            sendNotifications(recipientList, timeOfAcknowledgment, nc, priority, eventStateAcknowledged,
                    eventAlgo.getEventType(), messageText, NotifyType.ackNotification, null, null, null);
        }
    }

    public AlarmSummary getAlarmSummary() {
        final Boolean eventDetectionEnable = get(PropertyIdentifier.eventDetectionEnable);
        if (eventDetectionEnable != null && eventDetectionEnable.booleanValue()) {
            final EventState eventState = get(PropertyIdentifier.eventState);
            final NotifyType notifyType = get(PropertyIdentifier.notifyType);

            if (!EventState.normal.equals(eventState) && NotifyType.alarm.equals(notifyType))
                return new AlarmSummary( //
                        (ObjectIdentifier) get(PropertyIdentifier.objectIdentifier), //
                        eventState, //
                        (EventTransitionBits) get(PropertyIdentifier.ackedTransitions));
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public EventSummary getEventSummary() {
        final Boolean eventDetectionEnable = get(PropertyIdentifier.eventDetectionEnable);
        if (eventDetectionEnable != null && eventDetectionEnable.booleanValue()) {
            final EventState eventState = get(PropertyIdentifier.eventState);
            final EventTransitionBits ackedTransitions = get(PropertyIdentifier.ackedTransitions);

            if (!EventState.normal.equals(eventState) || !ackedTransitions.allTrue()) {
                final UnsignedInteger ncId = get(PropertyIdentifier.notificationClass);
                final BACnetObject nc = getLocalDevice()
                        .getObject(new ObjectIdentifier(ObjectType.notificationClass, ncId.intValue()));

                return new EventSummary( //
                        (ObjectIdentifier) get(PropertyIdentifier.objectIdentifier), //
                        eventState, //
                        (EventTransitionBits) get(PropertyIdentifier.ackedTransitions), //
                        (BACnetArray<TimeStamp>) get(PropertyIdentifier.eventTimeStamps), //
                        (NotifyType) get(PropertyIdentifier.notifyType), //
                        (EventTransitionBits) get(PropertyIdentifier.eventEnable), //
                        (BACnetArray<UnsignedInteger>) nc.get(PropertyIdentifier.priority));
            }
        }

        return null;
    }

    public EnrollmentSummary getEnrollmentSummary(final AcknowledgmentFilter acknowledgmentFilter,
            final RecipientProcess enrollmentFilter, final EventStateFilter eventStateFilter,
            final EventType eventTypeFilter, final PriorityFilter priorityFilter,
            final UnsignedInteger notificationClassFilter) {
        final Boolean eventDetectionEnable = get(PropertyIdentifier.eventDetectionEnable);
        if (eventDetectionEnable != null && eventDetectionEnable.booleanValue()) {
            final EventState eventState = get(PropertyIdentifier.eventState);
            final UnsignedInteger ncId = get(PropertyIdentifier.notificationClass);
            final BACnetObject nc = getLocalDevice()
                    .getObject(new ObjectIdentifier(ObjectType.notificationClass, ncId.intValue()));
            final BACnetArray<UnsignedInteger> priorities = nc.get(PropertyIdentifier.priority);
            final UnsignedInteger priority = priorities.get(eventState.getTransitionIndex());

            boolean include = true;

            // Acknowledgment filter
            final EventTransitionBits ackedTransitions = get(PropertyIdentifier.ackedTransitions);
            if (AcknowledgmentFilter.acked.equals(acknowledgmentFilter) && !ackedTransitions.allTrue())
                include = false;
            if (AcknowledgmentFilter.notAcked.equals(acknowledgmentFilter) && ackedTransitions.allTrue())
                include = false;

            // Enrollment filter
            if (enrollmentFilter != null) {
                final SequenceOf<Destination> recipientList = nc.get(PropertyIdentifier.recipientList);
                boolean found = false;
                for (final Destination destination : recipientList) {
                    if (destination.getRecipient().equals(enrollmentFilter.getRecipient()) && //
                            destination.getProcessIdentifier().equals(enrollmentFilter.getProcessIdentifier())) {
                        found = true;
                        break;
                    }
                }

                if (!found)
                    include = false;
            }

            // Event state filter
            if (eventStateFilter != null) {
                if (EventStateFilter.offnormal.equals(eventStateFilter) && !eventState.isOffNormal())
                    include = false;
                if (EventStateFilter.fault.equals(eventStateFilter) && !eventState.equals(EventState.fault))
                    include = false;
                if (EventStateFilter.normal.equals(eventStateFilter) && !eventState.equals(EventState.normal))
                    include = false;
                if (EventStateFilter.active.equals(eventStateFilter) && eventState.equals(EventState.normal))
                    include = false;
            }

            // Event type filter
            if (eventTypeFilter != null && !eventTypeFilter.equals(eventAlgo.getEventType()))
                include = false;

            // Priority filter
            if (priorityFilter != null) {
                if (priority.intValue() < priorityFilter.getMinPriority().intValue())
                    include = false;
                if (priority.intValue() > priorityFilter.getMaxPriority().intValue())
                    include = false;
            }

            // Notification class filter
            if (notificationClassFilter != null && !notificationClassFilter.equals(ncId))
                include = false;

            if (include)
                return new EnrollmentSummary((ObjectIdentifier) get(PropertyIdentifier.objectIdentifier),
                        eventAlgo.getEventType(), eventState, priority, ncId);
        }

        return null;
    }

    private void sendNotifications(final SequenceOf<Destination> recipientList, final TimeStamp timeStamp,
            final BACnetObject nc, final BACnetArray<UnsignedInteger> priority, final EventState toState,
            final EventType eventType, final CharacterString messageText, final NotifyType notifyType,
            final Boolean ackRequired, final EventState fromState, final NotificationParameters eventValues) {
        for (final Destination destination : recipientList) {
            if (destination.isSuitableForEvent(timeStamp, toState)) {
                Address address;
                if (destination.getRecipient().isAddress())
                    address = destination.getRecipient().getAddress();
                else {
                    final int deviceId = destination.getRecipient().getDevice().getInstanceNumber();
                    try {
                        final RemoteDevice rd = getLocalDevice().getRemoteDevice(deviceId).get();
                        address = rd.getAddress();
                    } catch (final BACnetException e) {
                        LOG.warn("Unknown device id {}, send failed", deviceId, e);
                        continue;
                    }
                }

                LOG.debug("Sending {} to {}", notifyType, destination.getRecipient());

                final UnsignedInteger processIdentifier = destination.getProcessIdentifier();
                final ObjectIdentifier initiatingDeviceIdentifier = getLocalDevice().getId();
                final ObjectIdentifier eventObjectIdentifier = (ObjectIdentifier) get(
                        PropertyIdentifier.objectIdentifier);
                final UnsignedInteger notificationClass = (UnsignedInteger) nc
                        .get(PropertyIdentifier.notificationClass);
                final UnsignedInteger priorityNum = priority.get(toState.getTransitionIndex());

                if (destination.getIssueConfirmedNotifications().booleanValue()) {
                    // Confirmed notification
                    final ConfirmedEventNotificationRequest req = new ConfirmedEventNotificationRequest(
                            processIdentifier, initiatingDeviceIdentifier, eventObjectIdentifier, timeStamp,
                            notificationClass, priorityNum, eventType, messageText, notifyType, ackRequired, fromState,
                            toState, eventValues);
                    getLocalDevice().send(address, req, null);
                } else {
                    // Unconfirmed notification
                    final UnconfirmedEventNotificationRequest req = new UnconfirmedEventNotificationRequest(
                            processIdentifier, initiatingDeviceIdentifier, eventObjectIdentifier, timeStamp,
                            notificationClass, priorityNum, eventType, messageText, notifyType, ackRequired, fromState,
                            toState, eventValues);
                    getLocalDevice().send(address, req);
                }

                // Internal (proprietary) handling of notifications for NotificationClass objects.
                // If the nc is a NotificationClassObject, provide notification to it directly as well.
                if (nc instanceof NotificationClassObject) {
                    final NotificationClassObject nco = (NotificationClassObject) nc;
                    nco.fireEventNotification(eventObjectIdentifier, timeStamp, notificationClass, priorityNum,
                            eventType, messageText, notifyType, ackRequired, fromState, toState, eventValues);
                }
            }
        }
    }
}
