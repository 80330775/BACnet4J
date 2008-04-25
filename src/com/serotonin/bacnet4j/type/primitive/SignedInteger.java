package com.serotonin.bacnet4j.type.primitive;

import java.math.BigInteger;

import com.serotonin.util.queue.ByteQueue;

public class SignedInteger extends Primitive {
    public static final byte TYPE_ID = 3;
    
    private int smallValue;
    private BigInteger bigValue;
    
    public SignedInteger(int value) {
        smallValue = value;
    }
    
    public SignedInteger(BigInteger value) {
        bigValue = value;
    }
    
    public int intValue() {
        if (bigValue == null)
            return smallValue;
        return bigValue.intValue();
    }
    
    public BigInteger bigIntegerValue() {
        if (bigValue == null)
            return BigInteger.valueOf(smallValue);
        return bigValue;
    }
    
    //
    // Reading and writing
    //
    public SignedInteger(ByteQueue queue) {
        // Read the data length value.
        int length = (int)readTag(queue);
        
        byte[] bytes = new byte[length];
        queue.pop(bytes);
        BigInteger bi = new BigInteger(bytes);
        
        if (length < 5)
            smallValue = bi.intValue();
        else
            bigValue = bi;
    }
    
    public void writeImpl(ByteQueue queue) {
        if (bigValue == null) {
            long length = getLength();
            while (length > 0)
                queue.push(smallValue >> (--length * 8));
        }
        else
            queue.push(bigValue.toByteArray());
    }

    protected long getLength() {
        if (bigValue == null) {
            int length;
            if (smallValue < Byte.MAX_VALUE && smallValue > Byte.MIN_VALUE)
                length = 1;
            else if (smallValue < Short.MAX_VALUE && smallValue > Short.MAX_VALUE)
                length = 2;
            else if (smallValue < 8388607 && smallValue > -8388608)
                length = 3;
            else
                length = 4;
            return length;
        }
        return bigValue.toByteArray().length;
    }

    protected byte getTypeId() {
        return TYPE_ID;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((bigValue == null) ? 0 : bigValue.hashCode());
        result = PRIME * result + smallValue;
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
        final SignedInteger other = (SignedInteger) obj;
        return this.bigIntegerValue().equals(other.bigIntegerValue());
    }
    
    public String toString() {
        if (bigValue == null)
            return Integer.toString(smallValue);
        return bigValue.toString();
    }
}