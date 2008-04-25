package com.serotonin.bacnet4j.service.confirmed;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.NotImplementedException;
import com.serotonin.bacnet4j.service.acknowledgement.AcknowledgementService;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.util.queue.ByteQueue;

public class RemoveListElementRequest extends ConfirmedRequestService {
    public static final byte TYPE_ID = 9;
    
    private ObjectIdentifier eventObjectIdentifier;
    private PropertyIdentifier propertyIdentifier;
    private UnsignedInteger propertyArrayIndex;
    private SequenceOf<ReadAccessSpecification> listOfElements;
    
    public RemoveListElementRequest(ObjectIdentifier eventObjectIdentifier, PropertyIdentifier propertyIdentifier, 
            UnsignedInteger propertyArrayIndex, SequenceOf<ReadAccessSpecification> listOfElements) {
        this.eventObjectIdentifier = eventObjectIdentifier;
        this.propertyIdentifier = propertyIdentifier;
        this.propertyArrayIndex = propertyArrayIndex;
        this.listOfElements = listOfElements;
    }

    @Override
    public byte getChoiceId() {
        return TYPE_ID;
    }
    
    @Override
    public void write(ByteQueue queue) {
        write(queue, eventObjectIdentifier, 0);
        write(queue, propertyIdentifier, 1);
        writeOptional(queue, propertyArrayIndex, 2);
        write(queue, listOfElements, 3);
    }
    
    RemoveListElementRequest(ByteQueue queue) throws BACnetException {
        eventObjectIdentifier = read(queue, ObjectIdentifier.class, 0);
        propertyIdentifier = read(queue, PropertyIdentifier.class, 1);
        propertyArrayIndex = readOptional(queue, UnsignedInteger.class, 2);
        listOfElements = readSequenceOf(queue, ReadAccessSpecification.class, 3);
    }

    @Override
    public AcknowledgementService handle(LocalDevice localDevice, Address from) throws BACnetException {
        throw new NotImplementedException();
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((eventObjectIdentifier == null) ? 0 : eventObjectIdentifier.hashCode());
        result = PRIME * result + ((listOfElements == null) ? 0 : listOfElements.hashCode());
        result = PRIME * result + ((propertyArrayIndex == null) ? 0 : propertyArrayIndex.hashCode());
        result = PRIME * result + ((propertyIdentifier == null) ? 0 : propertyIdentifier.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final RemoveListElementRequest other = (RemoveListElementRequest) obj;
        if (eventObjectIdentifier == null) {
            if (other.eventObjectIdentifier != null)
                return false;
        }
        else if (!eventObjectIdentifier.equals(other.eventObjectIdentifier))
            return false;
        if (listOfElements == null) {
            if (other.listOfElements != null)
                return false;
        }
        else if (!listOfElements.equals(other.listOfElements))
            return false;
        if (propertyArrayIndex == null) {
            if (other.propertyArrayIndex != null)
                return false;
        }
        else if (!propertyArrayIndex.equals(other.propertyArrayIndex))
            return false;
        if (propertyIdentifier == null) {
            if (other.propertyIdentifier != null)
                return false;
        }
        else if (!propertyIdentifier.equals(other.propertyIdentifier))
            return false;
        return true;
    }
}