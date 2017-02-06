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
package com.serotonin.bacnet4j.type.enumerated;

import com.serotonin.bacnet4j.type.primitive.Enumerated;
import com.serotonin.bacnet4j.util.sero.ByteQueue;

/**
 * @author Matthew Lohbihler
 */
public class ShedState extends Enumerated {
    private static final long serialVersionUID = 1807549359103525990L;
    public static final ShedState shedInactive = new ShedState(0);
    public static final ShedState shedRequestPending = new ShedState(1);
    public static final ShedState shedCompliant = new ShedState(2);
    public static final ShedState shedNonCompliant = new ShedState(3);

    public static final ShedState[] ALL = { shedInactive, shedRequestPending, shedCompliant, shedNonCompliant, };

    public ShedState(final int value) {
        super(value);
    }

    public ShedState(final ByteQueue queue) {
        super(queue);
    }

    @Override
    public String toString() {
        final int type = intValue();
        if (type == shedInactive.intValue())
            return "shedInactive";
        if (type == shedRequestPending.intValue())
            return "shedRequestPending";
        if (type == shedCompliant.intValue())
            return "shedCompliant";
        if (type == shedNonCompliant.intValue())
            return "shedNonCompliant";
        return "Unknown: " + type;
    }
}
