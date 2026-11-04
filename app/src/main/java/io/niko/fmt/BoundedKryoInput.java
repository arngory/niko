package io.niko.fmt;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferInput;

/**
 * Kryo input over a byte array that refuses a declared size larger than the data left. Kryo allocates the array
 * for a string or byte field before reading it: a few bytes of a shared link could ask for gigabytes.
 */
public class BoundedKryoInput extends ByteBufferInput {

    public BoundedKryoInput(byte[] bytes) {
        super(bytes);
    }

    private void check(long size) {
        if (size < 0 || size > limit() - position()) {
            throw new KryoException("a field of " + size + " bytes in " + (limit() - position()) + " left");
        }
    }

    @Override
    public String readString() {
        // UTF-8 (the flag bit): a char count comes first, and each char takes at least a byte.
        // ASCII is read up to its end marker and needs no check.
        if (position() < limit() && readVarIntFlag()) {
            int start = position();
            int count = readVarIntFlag(true);
            int afterLength = position();
            setPosition(start);
            // 0 is null, 1 the empty string, n is n - 1 chars
            if (count > 1) {
                if (count - 1 > limit() - afterLength) {
                    throw new KryoException("a string of " + (count - 1) + " chars in " + (limit() - afterLength) + " bytes left");
                }
            }
        }
        return super.readString();
    }

    @Override
    public byte[] readBytes(int length) throws KryoException {
        check(length);
        return super.readBytes(length);
    }
}
