package com.github.ruediste.p2psync.node;

import java.util.Locale;
import java.util.Objects;

import com.github.ruediste.p2psync.identity.UserId;

public record Contact(String contactId, UserId userId, String firstName, String lastName, String email,
        String phone, String notes) {

    public enum Field {
        USER_ID,
        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        PHONE,
        NOTES
    }

    public Contact {
        Objects.requireNonNull(contactId);
        firstName = firstName == null ? "" : firstName;
        lastName = lastName == null ? "" : lastName;
        email = email == null ? "" : email;
        phone = phone == null ? "" : phone;
        notes = notes == null ? "" : notes;
    }

    public boolean isEmpty() {
        return userId == null && firstName.isBlank() && lastName.isBlank() && email.isBlank()
                && phone.isBlank() && notes.isBlank();
    }

    public String displayName() {
        String name = (firstName + " " + lastName).trim();
        if (!name.isEmpty())
            return name;
        if (!email.isBlank())
            return email;
        if (!phone.isBlank())
            return phone;
        if (userId != null)
            return "User " + ContactList.shortKey(userId);
        return "Contact " + contactId.substring(0, Math.min(8, contactId.length()));
    }

    public Contact withField(Field field, String textValue, UserId userValue) {
        return switch (field) {
            case USER_ID -> new Contact(contactId, userValue, firstName, lastName, email, phone, notes);
            case FIRST_NAME -> new Contact(contactId, userId, textValue, lastName, email, phone, notes);
            case LAST_NAME -> new Contact(contactId, userId, firstName, textValue, email, phone, notes);
            case EMAIL -> new Contact(contactId, userId, firstName, lastName, textValue, phone, notes);
            case PHONE -> new Contact(contactId, userId, firstName, lastName, email, textValue, notes);
            case NOTES -> new Contact(contactId, userId, firstName, lastName, email, phone, textValue);
        };
    }

    public String searchText() {
        return (firstName + "\n" + lastName + "\n" + email + "\n" + phone + "\n" + notes + "\n"
                + contactId + "\n" + (userId == null ? "" : ContactList.hex(userId))).toLowerCase(Locale.ROOT);
    }
}
