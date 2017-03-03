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
package com.serotonin.bacnet4j.obj.mixin;

import static com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier.minimumOffTime;
import static com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier.minimumOnTime;
import static com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier.outOfService;
import static com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier.presentValue;
import static com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier.priorityArray;
import static com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier.relinquishDefault;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.AbstractMixin;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.constructed.PriorityArray;
import com.serotonin.bacnet4j.type.constructed.PriorityValue;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Boolean;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class CommandableMixin extends AbstractMixin {
    static final Logger LOG = LoggerFactory.getLogger(CommandableMixin.class);

    private boolean overridden;
    private boolean commandable;

    // Runtime
    private ScheduledFuture<?> minOnOffTimerTask;

    public CommandableMixin(final BACnetObject bo) {
        super(bo);
    }

    public boolean isOverridden() {
        return overridden;
    }

    public void setOverridden(final boolean overridden) {
        this.overridden = overridden;
    }

    public boolean isCommandable() {
        return commandable;
    }

    public void setCommandable(final Encodable relqDefault) {
        commandable = true;

        // If the object does not have a priority array and relinquish default, add them
        addProperty(priorityArray, new PriorityArray(), false);
        addProperty(relinquishDefault, relqDefault, false);
    }

    @Override
    synchronized protected boolean validateProperty(final PropertyValue value) throws BACnetServiceException {
        if (presentValue.equals(value.getPropertyIdentifier())) {
            if (overridden)
                return false;

            final Boolean oos = get(outOfService);
            if (oos.booleanValue())
                return false;

            if (commandable && value.getValue() instanceof Null)
                return true;
        }

        return false;
    }

    @Override
    synchronized protected boolean writeProperty(final PropertyValue value) throws BACnetServiceException {
        if (presentValue.equals(value.getPropertyIdentifier())) {
            if (overridden)
                // Never allow a write if the object is overridden.
                throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);

            if (commandable) {
                command(value.getValue(), value.getPriority());
                return true;
            }

            // Not commandable.
            final Boolean oos = get(outOfService);
            if (oos.booleanValue()) {
                // Writable while the object is out of service.
                writePropertyInternal(presentValue, value.getValue());
                return true;
            }

            // Not writable while the object is in service and not commandable.
            // ?? Is this correct, or should this be configurably allowable?
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);
        }

        return false;
    }

    @Override
    synchronized protected void afterWriteProperty(final PropertyIdentifier pid, final Encodable oldValue,
            final Encodable newValue) {
        if (relinquishDefault.equals(pid))
            // The relinquish default was changed. Ensure that the present value gets updated if necessary.
            updatePresentValue(null);
    }

    private void command(final Encodable value, final UnsignedInteger priority) throws BACnetServiceException {
        int pri = 16;
        if (priority != null)
            pri = priority.intValue();

        if (pri < 1 || pri > 16)
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.invalidArrayIndex);

        // Cannot set to priority level 6 - reserved for minimum_on_time and minimum_off_time functioning.
        if (pri == 6)
            throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);

        // Set the value in the priority array.
        final PriorityArray priArr = get(priorityArray);
        priArr.set(pri, new PriorityValue(value));

        // If a non-null value is written above level 6 while min time is in effect, it overwrites the current min time.
        if (pri < 6 && !(value instanceof Null)) {
            if (minOnOffTimerTask != null) {
                // Cancel the task only if the present value changes.
                final Encodable pv = get(presentValue);
                if (!value.equals(pv)) {
                    LOG.debug("Cancelling timer due to different value at higher priority");
                    minOnOffTimerTask.cancel(false);
                    minOnOffTimerTask = null;
                    priArr.set(6, new PriorityValue(new Null()));
                }
            }
        }

        updatePresentValue(priArr);
    }

    private void updatePresentValue(final PriorityArray priorityArray) {
        final PriorityArray pa = priorityArray == null ? get(PropertyIdentifier.priorityArray) : priorityArray;

        // Update the present value.
        final Encodable newValue = calculatePresentValue(pa);

        // Minimum on/off time
        final UnsignedInteger minOff = get(minimumOffTime);
        final UnsignedInteger minOn = get(minimumOnTime);
        if (minOff != null && minOn != null) {
            // If a timer task exists, there is no action to take.
            if (minOnOffTimerTask == null) {
                final Encodable oldValue = get(presentValue);
                if (!newValue.equals(oldValue)) {
                    // Change of state.
                    pa.set(6, new PriorityValue(newValue));

                    int time;
                    if (BinaryPV.inactive.equals(newValue)) {
                        time = minOff.intValue();
                        LOG.debug("Starting min off timer: {}s", time);
                    } else {
                        time = minOn.intValue();
                        LOG.debug("Starting min on timer: {}s", time);
                    }

                    minOnOffTimerTask = getLocalDevice().schedule(() -> {
                        // Min time has elapsed.
                        LOG.debug("Min off/on timer has expired");
                        minOnOffCompleted();
                    }, time, TimeUnit.SECONDS);
                }
            }

            //  to the high priority while minimum time is in effect, that time shall be observed before any change of state
            //        is made as a result of a value at a lower priority.
        }

        writePropertyInternal(presentValue, newValue);
    }

    private Encodable calculatePresentValue(final PriorityArray priorityArray) {
        // Update the present value.
        PriorityValue topValue = null;
        for (final PriorityValue priv : priorityArray) {
            if (!priv.isa(Null.class)) {
                topValue = priv;
                break;
            }
        }

        Encodable pv;
        if (topValue == null)
            pv = get(relinquishDefault);
        else
            pv = topValue.getValue();

        return pv;
    }

    synchronized void minOnOffCompleted() {
        minOnOffTimerTask = null;
        final PriorityArray priArr = get(priorityArray);
        priArr.set(6, new PriorityValue(new Null()));
        updatePresentValue(priArr);
    }
}
