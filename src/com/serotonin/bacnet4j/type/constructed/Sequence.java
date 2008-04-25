package com.serotonin.bacnet4j.type.constructed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.SequenceDefinition;
import com.serotonin.bacnet4j.type.SequenceDefinition.ElementSpecification;
import com.serotonin.util.queue.ByteQueue;

public class Sequence extends BaseType {
    private SequenceDefinition definition;
    private Map<String, Encodable> values;
    
    public Sequence(SequenceDefinition definition, Map<String, Encodable> values) {
        this.definition = definition;
        this.values = values;
    }

    public void write(ByteQueue queue) {
        List<ElementSpecification> specs = definition.getElements();
        for (ElementSpecification spec : specs) {
            if (spec.isOptional()) {
                if (spec.hasContextId())
                    writeOptional(queue, values.get(spec.getId()), spec.getContextId());
                else
                    writeOptional(queue, values.get(spec.getId()));
            }
            else {
                if (spec.hasContextId())
                    write(queue, values.get(spec.getId()), spec.getContextId());
                else
                    write(queue, values.get(spec.getId()));
            }
        }
    }
    
    public Sequence(SequenceDefinition definition, ByteQueue queue) throws BACnetException {
        values = new HashMap<String, Encodable>();
        List<ElementSpecification> specs = definition.getElements();
        for (int i=0; i<specs.size(); i++) {
            ElementSpecification spec = specs.get(i);
            if (spec.isSequenceOf()) {
                if (spec.isOptional())
                    values.put(spec.getId(), readOptionalSequenceOf(queue, spec.getClazz(), spec.getContextId()));
                else {
                    if (spec.hasContextId())
                        values.put(spec.getId(), readSequenceOf(queue, spec.getClazz(), spec.getContextId()));
                    else
                        values.put(spec.getId(), readSequenceOf(queue, spec.getClazz()));
                }
            }
            else if (spec.isOptional())
                values.put(spec.getId(), readOptional(queue, spec.getClazz(), spec.getContextId()));
            else if (spec.hasContextId())
                values.put(spec.getId(), read(queue, spec.getClazz(), spec.getContextId()));
            else
                values.put(spec.getId(), read(queue, spec.getClazz()));
        }
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((values == null) ? 0 : values.hashCode());
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
        final Sequence other = (Sequence) obj;
        if (values == null) {
            if (other.values != null)
                return false;
        }
        else if (!values.equals(other.values))
            return false;
        return true;
    }
}