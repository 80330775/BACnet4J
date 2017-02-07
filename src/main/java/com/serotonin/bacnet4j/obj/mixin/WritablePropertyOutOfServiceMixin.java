package com.serotonin.bacnet4j.obj.mixin;

import java.util.HashSet;
import java.util.Set;

import com.serotonin.bacnet4j.exception.BACnetServiceException;
import com.serotonin.bacnet4j.obj.AbstractMixin;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.type.constructed.PropertyValue;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.Boolean;

/**
 * Allows writing of the given properties only when the object is out of service.
 *
 * @author Matthew
 */
public class WritablePropertyOutOfServiceMixin extends AbstractMixin {
    private final Set<PropertyIdentifier> pids = new HashSet<>();

    public WritablePropertyOutOfServiceMixin(final BACnetObject bo, final PropertyIdentifier... pids) {
        super(bo);
        for (final PropertyIdentifier pid : pids)
            this.pids.add(pid);
    }

    @Override
    protected boolean writeProperty(final PropertyValue value) throws BACnetServiceException {
        final Boolean outOfService = get(PropertyIdentifier.outOfService);
        if (!outOfService.booleanValue()) {
            if (pids.contains(value.getPropertyIdentifier()))
                throw new BACnetServiceException(ErrorClass.property, ErrorCode.writeAccessDenied);
        }
        return false;
    }
}
