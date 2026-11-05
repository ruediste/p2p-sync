package com.github.ruediste.p2psync.storage;

import java.nio.ByteBuffer;

public interface BlockSerializer<T> {

    ByteBuffer serialize(T value);

    T deserialize(ByteBuffer buffer);

}