package com.github.ruediste.p2psync.node;

/**
 * Mutable view of the user-visible state used by {@link NodeUserHandle#modify}
 * and {@link NodeUserHandle#read}. It is only an in-memory facade; the
 * authoritative state lives in the user's file tree and contact list.
 */
public class DataUserRoot {
    public String userStatusMessage;
    public byte[] rootDirectoryGuid;
    public java.util.List<Contact> contacts = java.util.List.of();
}
