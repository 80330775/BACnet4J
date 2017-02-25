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
package com.serotonin.bacnet4j.service.confirmed;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.NotImplementedException;
import com.serotonin.bacnet4j.service.acknowledgement.AcknowledgementService;
import com.serotonin.bacnet4j.type.ThreadLocalObjectTypeStack;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.constructed.Choice;
import com.serotonin.bacnet4j.type.constructed.ChoiceOptions;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class CreateObjectRequest extends ConfirmedRequestService {
    public static final byte TYPE_ID = 10;

    private static ChoiceOptions choiceOptions = new ChoiceOptions();
    static {
        choiceOptions.addContextual(0, ObjectType.class);
        choiceOptions.addContextual(1, ObjectIdentifier.class);
    }

    private final Choice objectSpecifier;
    private final SequenceOf<PropertyValue> listOfInitialValues;

    public CreateObjectRequest(final ObjectType objectType, final SequenceOf<PropertyValue> listOfInitialValues) {
        objectSpecifier = new Choice(0, objectType, choiceOptions);
        this.listOfInitialValues = listOfInitialValues;
    }

    public CreateObjectRequest(final ObjectIdentifier objectIdentifier,
            final SequenceOf<PropertyValue> listOfInitialValues) {
        objectSpecifier = new Choice(1, objectIdentifier, choiceOptions);
        this.listOfInitialValues = listOfInitialValues;
    }

    @Override
    public byte getChoiceId() {
        return TYPE_ID;
    }

    @Override
    public AcknowledgementService handle(final LocalDevice localDevice, final Address from) throws BACnetException {
        // TODO object created this way should be the actual object classes where possible. This implies:
        // 1) A method to select the class to create based upon the provided object type
        // 2) A way to validate the given list of initial values in the same way that constructors validate parameters

        throw new NotImplementedException();

        //        ObjectIdentifier id;
        //        if (objectSpecifier.getContextId() == 0) {
        //            ObjectType type = (ObjectType) objectSpecifier.getDatum();
        //            id = localDevice.getNextInstanceObjectIdentifier(type);
        //        }
        //        else
        //            id = (ObjectIdentifier) objectSpecifier.getDatum();
        //
        //        BACnetObject obj = new BACnetObject(localDevice, id.getObjectType(), id.getInstanceNumber(), null);
        //
        //        if (listOfInitialValues != null) {
        //            for (int i = 0; i < listOfInitialValues.getCount(); i++) {
        //                PropertyValue pv = listOfInitialValues.get(i + 1);
        //                try {
        //                    obj.writeProperty(pv);
        //                }
        //                catch (BACnetServiceException e) {
        //                    throw new BACnetErrorException(new CreateObjectError(getChoiceId(), e, new UnsignedInteger(i + 1)));
        //                }
        //            }
        //        }
        //
        //        try {
        //            localDevice.addObject(obj);
        //        }
        //        catch (BACnetServiceException e) {
        //            throw new BACnetErrorException(new CreateObjectError(getChoiceId(), e, null));
        //        }
        //
        //        // Return a create object ack.
        //        return new CreateObjectAck(id);
    }

    @Override
    public void write(final ByteQueue queue) {
        write(queue, objectSpecifier, 0);
        writeOptional(queue, listOfInitialValues, 1);
    }

    CreateObjectRequest(final ByteQueue queue) throws BACnetException {
        objectSpecifier = readChoice(queue, choiceOptions, 0);
        try {
            if (objectSpecifier.isa(ObjectType.class))
                ThreadLocalObjectTypeStack.set((ObjectType) objectSpecifier.getDatum());
            else
                ThreadLocalObjectTypeStack.set(((ObjectIdentifier) objectSpecifier.getDatum()).getObjectType());
            listOfInitialValues = readOptionalSequenceOf(queue, PropertyValue.class, 1);
        } finally {
            ThreadLocalObjectTypeStack.remove();
        }
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (listOfInitialValues == null ? 0 : listOfInitialValues.hashCode());
        result = PRIME * result + (objectSpecifier == null ? 0 : objectSpecifier.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final CreateObjectRequest other = (CreateObjectRequest) obj;
        if (listOfInitialValues == null) {
            if (other.listOfInitialValues != null)
                return false;
        } else if (!listOfInitialValues.equals(other.listOfInitialValues))
            return false;
        if (objectSpecifier == null) {
            if (other.objectSpecifier != null)
                return false;
        } else if (!objectSpecifier.equals(other.objectSpecifier))
            return false;
        return true;
    }
}
