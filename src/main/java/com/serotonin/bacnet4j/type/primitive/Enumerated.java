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
package com.serotonin.bacnet4j.type.primitive;

import java.math.BigInteger;

import com.serotonin.bacnet4j.util.sero.ByteQueue;

public class Enumerated extends UnsignedInteger {
    private static final long serialVersionUID = 2462119559912570064L;
    public static final byte TYPE_ID = 9;

    public Enumerated(int value) {
        super(value);
    }

    public Enumerated(BigInteger value) {
        super(value);
    }

    public byte byteValue() {
        return (byte) intValue();
    }

    public boolean equals(Enumerated that) {
        if (that == null)
            return false;
        return intValue() == that.intValue();
    }

    public boolean isOneOf(Enumerated... those) {
        int id = intValue();
        for (Enumerated that : those) {
            if (id == that.intValue())
                return true;
        }
        return false;
    }

    //
    // Reading and writing
    //
    public Enumerated(ByteQueue queue) {
        super(queue);
    }

    @Override
    protected byte getTypeId() {
        return TYPE_ID;
    }
}
