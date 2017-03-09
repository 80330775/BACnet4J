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
package com.serotonin.bacnet4j.obj;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.exception.BACnetRuntimeException;
import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.mixin.CommandableMixin;
import com.serotonin.bacnet4j.obj.mixin.HasStatusFlagsMixin;
import com.serotonin.bacnet4j.obj.mixin.MultistateMixin;
import com.serotonin.bacnet4j.obj.mixin.PropertyListMixin;
import com.serotonin.bacnet4j.obj.mixin.event.IntrinsicReportingMixin;
import com.serotonin.bacnet4j.obj.mixin.event.eventAlgo.ChangeOfStateAlgo;
import com.serotonin.bacnet4j.obj.mixin.event.faultAlgo.FaultStateAlgo;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.DeviceObjectReference;
import com.serotonin.bacnet4j.type.constructed.EventTransitionBits;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.constructed.StatusFlags;
import com.serotonin.bacnet4j.type.constructed.ValueSource;
import com.serotonin.bacnet4j.type.enumerated.EventState;
import com.serotonin.bacnet4j.type.enumerated.NotifyType;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class MultistateValueObject extends BACnetObject {
    public MultistateValueObject(final LocalDevice localDevice, final int instanceNumber, final String name,
            final int numberOfStates, final BACnetArray<CharacterString> stateText, final int presentValue,
            final boolean outOfService) throws BACnetServiceException {
        super(localDevice, ObjectType.multiStateValue, instanceNumber, name);

        if (numberOfStates < 1)
            throw new BACnetRuntimeException("numberOfStates cannot be less than 1");

        final ValueSource valueSource = new ValueSource(new DeviceObjectReference(localDevice.getId(), getId()));

        writePropertyInternal(PropertyIdentifier.eventState, EventState.normal);
        writeProperty(valueSource, PropertyIdentifier.presentValue, new UnsignedInteger(presentValue));
        writePropertyInternal(PropertyIdentifier.outOfService, new Boolean(true));
        writePropertyInternal(PropertyIdentifier.statusFlags, new StatusFlags(false, false, false, true));

        // Mixins
        addMixin(new HasStatusFlagsMixin(this));
        addMixin(new CommandableMixin(this, PropertyIdentifier.presentValue));
        addMixin(new MultistateMixin(this));
        addMixin(new PropertyListMixin(this));

        writePropertyInternal(PropertyIdentifier.numberOfStates, new UnsignedInteger(numberOfStates));
        if (stateText != null)
            writeProperty(null, PropertyIdentifier.stateText, stateText);
        writeProperty(valueSource, PropertyIdentifier.presentValue, new UnsignedInteger(presentValue));
        if (!outOfService)
            writePropertyInternal(PropertyIdentifier.outOfService, new Boolean(outOfService));
    }

    public void supportIntrinsicReporting(final int timeDelay, final int notificationClass,
            final SequenceOf<UnsignedInteger> alarmValues, final SequenceOf<UnsignedInteger> faultValues,
            final EventTransitionBits eventEnable, final NotifyType notifyType, final int timeDelayNormal) {
        // Prepare the object with all of the properties that intrinsic reporting will need.
        // User-defined properties
        writePropertyInternal(PropertyIdentifier.timeDelay, new UnsignedInteger(timeDelay));
        writePropertyInternal(PropertyIdentifier.notificationClass, new UnsignedInteger(notificationClass));
        writePropertyInternal(PropertyIdentifier.alarmValues, alarmValues);
        if (faultValues != null)
            writePropertyInternal(PropertyIdentifier.faultValues, faultValues);
        writePropertyInternal(PropertyIdentifier.eventEnable, eventEnable);
        writePropertyInternal(PropertyIdentifier.notifyType, notifyType);
        writePropertyInternal(PropertyIdentifier.timeDelayNormal, new UnsignedInteger(timeDelayNormal));
        writePropertyInternal(PropertyIdentifier.eventDetectionEnable, new Boolean(true));

        // Now add the mixin.
        final ChangeOfStateAlgo eventAlgo = new ChangeOfStateAlgo(PropertyIdentifier.presentValue,
                PropertyIdentifier.alarmValues);
        final FaultStateAlgo faultAlgo = new FaultStateAlgo(PropertyIdentifier.reliability,
                PropertyIdentifier.faultValues);
        addMixin(new IntrinsicReportingMixin(this, eventAlgo, faultAlgo,
                new PropertyIdentifier[] { PropertyIdentifier.presentValue }));
    }

    public MultistateValueObject supportCovReporting() {
        _supportCovReporting(null);
        return this;
    }

    public MultistateValueObject supportCommandable(final UnsignedInteger relinquishDefault) {
        _supportCommandable(relinquishDefault);
        return this;
    }

    public MultistateValueObject supportValueSource() {
        _supportValueSource();
        return this;
    }
}
