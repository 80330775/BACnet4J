package com.serotonin.bacnet4j.event;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.RemoteObject;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.Choice;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.TimeStamp;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.EventType;
import com.serotonin.bacnet4j.type.enumerated.MessagePriority;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.notificationParameters.NotificationParameters;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

/**
 * Class to handle various events that occur on the local device. This class accepts 0 to many listeners, and uses
 * a executor queue to dispatch notifications where appropriate. 
 * @author mlohbihler
 */
public class DeviceEventHandler {
    private ExecutorService dispatchService;
    private final ConcurrentLinkedQueue<DeviceEventListener> listeners = new ConcurrentLinkedQueue<DeviceEventListener>();
    
    
    //
    ///
    /// Lifecycle
    ///
    //
    public void initialize() {
        dispatchService = Executors.newCachedThreadPool();
    }
    
    public void terminate() {
        ExecutorService temp = dispatchService;
        dispatchService = null;
        if (temp != null)
            temp.shutdown();
    }
    
    
    //
    ///
    /// Listener management
    ///
    //
    public void addListener(DeviceEventListener l) {
        listeners.add(l);
    }
    
    public void removeListener(DeviceEventListener l) {
        listeners.remove(l);
    }
    
    
    //
    ///
    /// Checks and notifications
    ///
    //
    public boolean checkAllowPropertyWrite(BACnetObject obj, PropertyValue pv) {
        for (DeviceEventListener l : listeners) {
            try {
                if (!l.allowPropertyWrite(obj, pv))
                    return false;
            }
            catch (Throwable e) {
                try {
                    l.listenerException(e);
                }
                catch (Throwable e1) {}
            }
        }
        return true;
    }
    
    public void fireIAmReceived(final RemoteDevice d) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.iAmReceived(d);
            }
        });
    }
    
    public void propertyWritten(final BACnetObject obj, final PropertyValue pv) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.propertyWritten(obj, pv);
            }
        });
    }
    
    public void fireIHaveReceived(final RemoteDevice d, final RemoteObject o) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.iHaveReceived(d, o);
            }
        });
    }
    
    public void fireCovNotification(final UnsignedInteger subscriberProcessIdentifier, 
            final RemoteDevice initiatingDevice, final ObjectIdentifier monitoredObjectIdentifier,
            final UnsignedInteger timeRemaining, final SequenceOf<PropertyValue> listOfValues) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.covNotificationReceived(subscriberProcessIdentifier, initiatingDevice,
                        monitoredObjectIdentifier, timeRemaining, listOfValues);
            }
        });
    }
    
    public void fireEventNotification(final UnsignedInteger processIdentifier, 
            final RemoteDevice initiatingDevice, final ObjectIdentifier eventObjectIdentifier, 
            final TimeStamp timeStamp, final UnsignedInteger notificationClass, final UnsignedInteger priority, 
            final EventType eventType, final CharacterString messageText, final NotifyType notifyType,
            final Boolean ackRequired, final EventState fromState, final EventState toState, 
            final NotificationParameters eventValues) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.eventNotificationReceived(processIdentifier, initiatingDevice, eventObjectIdentifier,
                        timeStamp, notificationClass, priority, eventType, messageText, notifyType, ackRequired, 
                        fromState, toState, eventValues);
            }
        });
    }
    
    public void fireTextMessage(final RemoteDevice textMessageSourceDevice, final Choice messageClass, 
            final MessagePriority messagePriority, final CharacterString message) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.textMessageReceived(textMessageSourceDevice, messageClass, messagePriority, message);
            }
        });
    }

    public void firePrivateTransfer(final UnsignedInteger vendorId, final UnsignedInteger serviceNumber,
            final Encodable serviceParameters) {
        multicast(new EventDispatcher() {
            public void dispatch(DeviceEventListener l) {
                l.privateTransferReceived(vendorId, serviceNumber, serviceParameters);
            }
        });
    }
    
    /**
     * Creates an event multicaster and gives it to the execution service for running out of process.
     * @param dispatcher
     */
    private void multicast(EventDispatcher dispatcher) {
        if (dispatchService == null)
            throw new IllegalStateException("DeviceEventHandler has not been initialized");
        dispatchService.execute(new EventMulticaster(dispatcher));
    }
   
    /**
     * Class for dispatching an event to multiple listeners
     * @author mlohbihler
     */
    private class EventMulticaster implements Runnable {
        EventDispatcher dispatcher;
        
        EventMulticaster(EventDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }
        
        public void run() {
            for (DeviceEventListener l : listeners) {
                try {
                    dispatcher.dispatch(l);
                }
                catch (Throwable e) {
                    try {
                        l.listenerException(e);
                    }
                    catch (Throwable e1) {}
                }
            }
        }
    }
    
    /**
     * Interface for defining how a particular event is dispatched to listeners
     * @author mlohbihler
     */
    private interface EventDispatcher {
        void dispatch(DeviceEventListener l);
    }
}