package com.github.ruediste.p2psync.node;

import java.util.HashMap;
import java.util.Optional;

import com.github.ruediste.p2psync.libp2p.security.CryptoUtils;
import com.google.protobuf.GeneratedMessage;

public class BlockStorage {

    private HashMap<BlockId, byte[]> blocks = new HashMap<>();

    public Optional<byte[]> getBlock(BlockId id) {
        return Optional.ofNullable(blocks.get(id));
    }

    public void put(BlockId id, byte[] bytes) {
        blocks.put(id, bytes);
    }

    public void remove(BlockId id) {
        blocks.remove(id);
    }

    public BlockId store(GeneratedMessage proto) {
        var data = proto.toByteArray();
        var id = new BlockId(CryptoUtils.sha256(data));
        put(id, data);
        return id;
    }

}
