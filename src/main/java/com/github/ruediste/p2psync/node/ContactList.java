package com.github.ruediste.p2psync.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.github.ruediste.p2psync.identity.UserId;
import com.github.ruediste.p2psync.proto.Sync;
import com.google.protobuf.ByteString;

final class ContactList {
    private static final Comparator<Contact> ORDER = Comparator
            .comparing(Contact::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Contact::contactId);

    private final UserId owner;
    private final Map<String, Contact> contacts = new TreeMap<>();

    private ContactList(UserId owner) {
        this.owner = owner;
    }

    static ContactList empty(UserId owner) {
        return new ContactList(owner);
    }

    static ContactList from(UserId owner, Iterable<Sync.ContactProto> stored) {
        var result = new ContactList(owner);
        for (var contact : stored) {
            var parsed = fromProto(contact);
            if (parsed != null && !parsed.isEmpty())
                result.put(parsed);
        }
        return result;
    }

    static ContactList copy(UserId owner, ContactList source) {
        var result = new ContactList(owner);
        result.contacts.putAll(source.contacts);
        return result;
    }

    List<Contact> list() {
        var result = new ArrayList<>(contacts.values());
        result.sort(ORDER);
        return List.copyOf(result);
    }

    Optional<Contact> get(String contactId) {
        return Optional.ofNullable(contacts.get(contactId));
    }

    Optional<Contact> findByUserId(UserId userId) {
        return contacts.values().stream().filter(contact -> userId.equals(contact.userId())).findFirst();
    }

    List<Contact> search(String query) {
        if (query == null || query.isBlank())
            return list();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        var result = contacts.values().stream()
                .filter(contact -> contact.searchText().contains(needle))
                .toList();
        var sorted = new ArrayList<>(result);
        sorted.sort(ORDER);
        return List.copyOf(sorted);
    }

    void apply(Sync.UserOperationProto operation) {
        try {
            switch (operation.getKind()) {
                case CONTACT_CREATE -> applyCreate(operation);
                case CONTACT_UPDATE -> applyUpdate(operation);
                case CONTACT_DELETE -> contacts.remove(hex(operation.getContactId().toByteArray()));
                default -> {
                }
            }
        } catch (IllegalArgumentException invalidOperation) {
            // A malformed remote operation is skipped; state corruption and genuine
            // bugs surface as other exception types and are not swallowed here.
        }
    }

    private void applyCreate(Sync.UserOperationProto operation) {
        if (!operation.hasContact())
            return;
        Contact contact = fromProto(operation.getContact());
        if (contact == null || contact.isEmpty())
            return;
        if (owner.equals(contact.userId()))
            contact = contact.withField(Contact.Field.USER_ID, null, null);
        put(contact);
    }

    private void applyUpdate(Sync.UserOperationProto operation) {
        String contactId = hex(operation.getContactId().toByteArray());
        Contact contact = contacts.get(contactId);
        if (contact == null)
            return;
        Contact.Field field = fieldFor(operation.getContactField());
        if (field == null)
            return;
        Contact updated;
        if (field == Contact.Field.USER_ID) {
            byte[] userIdBytes = operation.getContactUserId().toByteArray();
            UserId userId = userIdBytes.length == 0 ? null : UserId.fromBytes(userIdBytes);
            if (owner.equals(userId))
                return;
            updated = contact.withField(Contact.Field.USER_ID, null, userId);
        } else {
            updated = contact.withField(field, operation.getContactValue(), null);
        }
        if (updated.isEmpty()) {
            contacts.remove(contactId);
        } else {
            put(updated);
        }
    }

    private void put(Contact contact) {
        if (contact.userId() != null) {
            for (var other : new ArrayList<>(contacts.values())) {
                if (!other.contactId().equals(contact.contactId()) && contact.userId().equals(other.userId())) {
                    Contact unlinked = other.withField(Contact.Field.USER_ID, null, null);
                    if (unlinked.isEmpty())
                        contacts.remove(other.contactId());
                    else
                        contacts.put(other.contactId(), unlinked);
                }
            }
        }
        contacts.put(contact.contactId(), contact);
    }

    List<Sync.ContactProto> toProto() {
        var result = new ArrayList<Sync.ContactProto>();
        contacts.values().stream().sorted(Comparator.comparing(Contact::contactId))
                .forEach(contact -> result.add(toProto(contact)));
        return result;
    }

    static Sync.ContactProto toProto(Contact contact) {
        var builder = Sync.ContactProto.newBuilder()
                .setContactId(ByteString.copyFrom(HexFormat.of().parseHex(contact.contactId())))
                .setFirstName(contact.firstName())
                .setLastName(contact.lastName())
                .setEmail(contact.email())
                .setPhone(contact.phone())
                .setNotes(contact.notes());
        if (contact.userId() != null)
            builder.setUserId(ByteString.copyFrom(contact.userId().bytes()));
        return builder.build();
    }

    static Contact fromProto(Sync.ContactProto stored) {
        byte[] id = stored.getContactId().toByteArray();
        if (id.length != 40)
            throw new IllegalArgumentException("Invalid contact ID");
        UserId userId = stored.getUserId().isEmpty() ? null : UserId.fromBytes(stored.getUserId().toByteArray());
        return new Contact(hex(id), userId, stored.getFirstName(), stored.getLastName(), stored.getEmail(),
                stored.getPhone(), stored.getNotes());
    }

    private static Contact.Field fieldFor(Sync.ContactField field) {
        return switch (field) {
            case CONTACT_FIELD_USER_ID -> Contact.Field.USER_ID;
            case CONTACT_FIELD_FIRST_NAME -> Contact.Field.FIRST_NAME;
            case CONTACT_FIELD_LAST_NAME -> Contact.Field.LAST_NAME;
            case CONTACT_FIELD_EMAIL -> Contact.Field.EMAIL;
            case CONTACT_FIELD_PHONE -> Contact.Field.PHONE;
            case CONTACT_FIELD_NOTES -> Contact.Field.NOTES;
            default -> null;
        };
    }

    static Sync.ContactField protoField(Contact.Field field) {
        return switch (field) {
            case USER_ID -> Sync.ContactField.CONTACT_FIELD_USER_ID;
            case FIRST_NAME -> Sync.ContactField.CONTACT_FIELD_FIRST_NAME;
            case LAST_NAME -> Sync.ContactField.CONTACT_FIELD_LAST_NAME;
            case EMAIL -> Sync.ContactField.CONTACT_FIELD_EMAIL;
            case PHONE -> Sync.ContactField.CONTACT_FIELD_PHONE;
            case NOTES -> Sync.ContactField.CONTACT_FIELD_NOTES;
        };
    }

    static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    static String hex(UserId id) {
        return hex(id.bytes());
    }

    static String shortKey(UserId id) {
        byte[] bytes = id.bytes();
        return HexFormat.of().formatHex(bytes, 0, Math.min(4, bytes.length));
    }
}
