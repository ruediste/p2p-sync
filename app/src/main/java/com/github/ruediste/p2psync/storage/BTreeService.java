package com.github.ruediste.p2psync.storage;

/**
 * Service for managing B-Trees within the data storage system. The tree
 * follows the B-Tree implementation of BTRFS
 */
public class BTreeService {

    private DataStorage dataStorage;

    public BTreeService(DataStorage dataStorage) {
        this.dataStorage = dataStorage;
    }
}
