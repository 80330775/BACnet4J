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
package com.serotonin.bacnet4j.util;

import java.util.HashMap;
import java.util.Map;

import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;

public class DevicesObjectPropertyValues {
    private final Map<Integer, PropertyValues> values = new HashMap<>();

    public DevicesObjectPropertyValues add(final int deviceId, final ObjectType objectType, final int objectNumber,
            final PropertyIdentifier pid, final int pin, final Encodable value) {
        return add(deviceId, new ObjectIdentifier(objectType, objectNumber), pid, new UnsignedInteger(pin), value);
    }

    public DevicesObjectPropertyValues add(final int deviceId, final ObjectType objectType, final int objectNumber,
            final PropertyIdentifier pid, final UnsignedInteger pin, final Encodable value) {
        return add(deviceId, new ObjectIdentifier(objectType, objectNumber), pid, pin, value);
    }

    public DevicesObjectPropertyValues add(final int deviceId, final ObjectIdentifier oid, final PropertyIdentifier pid,
            final UnsignedInteger pin, final Encodable value) {
        PropertyValues propertyValues = values.get(deviceId);
        if (propertyValues == null) {
            propertyValues = new PropertyValues();
            values.put(deviceId, propertyValues);
        }
        propertyValues.add(oid, pid, pin, value);
        return this;
    }

    public PropertyValues getPropertyValues(final int deviceId) {
        return values.get(deviceId);
    }

    public int size() {
        int sum = 0;
        for (final PropertyValues pvs : values.values()) {
            sum += pvs.size();
        }
        return sum;
    }

    public void clear() {
        values.clear();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (values == null ? 0 : values.hashCode());
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
        final DevicesObjectPropertyValues other = (DevicesObjectPropertyValues) obj;
        if (values == null) {
            if (other.values != null)
                return false;
        } else if (!values.equals(other.values))
            return false;
        return true;
    }
}
