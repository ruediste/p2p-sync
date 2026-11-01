package com.github.ruediste.p2psync.node;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.ruediste.p2psync.proto.Sync;

/**
 * A read-through view over the USER and HOME operation maps. Mutations are
 * routed by operation kind so the public in-memory API keeps behaving like the
 * old single history while the storage layer keeps the units separate.
 */
final class CombinedOperationTree extends AbstractMap<Node.OperationId, Node.Operation>
        implements NavigableMap<Node.OperationId, Node.Operation> {
    private final TreeMap<Node.OperationId, Node.Operation> user;
    private final TreeMap<Node.OperationId, Node.Operation> home;

    CombinedOperationTree(TreeMap<Node.OperationId, Node.Operation> user,
            TreeMap<Node.OperationId, Node.Operation> home) {
        this.user = user;
        this.home = home;
    }

    TreeMap<Node.OperationId, Node.Operation> userMap() {
        return user;
    }

    TreeMap<Node.OperationId, Node.Operation> homeMap() {
        return home;
    }

    static boolean isFileOperation(Node.Operation operation) {
        try {
            return switch (Sync.UserOperation.parseFrom(operation.payload).getKind()) {
                case SET_STATUS, CREATE_DIRECTORY, CREATE_FILE, WRITE_FILE, TRUNCATE_FILE, MOVE, RENAME,
                        DELETE -> true;
                default -> false;
            };
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            return false;
        }
    }

    private TreeMap<Node.OperationId, Node.Operation> target(Node.Operation value) {
        return isFileOperation(value) ? home : user;
    }

    private TreeMap<Node.OperationId, Node.Operation> snapshot() {
        var result = new TreeMap<Node.OperationId, Node.Operation>(user);
        result.putAll(home);
        return result;
    }

    @Override
    public Node.Operation put(Node.OperationId key, Node.Operation value) {
        var removed = user.remove(key);
        removed = home.remove(key) == null ? removed : null;
        target(value).put(key, value);
        return removed;
    }

    @Override
    public Node.Operation remove(Object key) {
        var result = user.remove(key);
        var other = home.remove(key);
        return result == null ? other : result;
    }

    @Override
    public void putAll(java.util.Map<? extends Node.OperationId, ? extends Node.Operation> map) {
        for (var entry : map.entrySet())
            put(entry.getKey(), entry.getValue());
    }

    @Override
    public void clear() {
        user.clear();
        home.clear();
    }

    @Override
    public Node.Operation get(Object key) {
        var result = user.get(key);
        return result == null ? home.get(key) : result;
    }

    @Override
    public boolean containsKey(Object key) {
        return user.containsKey(key) || home.containsKey(key);
    }

    @Override
    public int size() {
        return user.size() + home.size();
    }

    @Override
    public boolean isEmpty() {
        return user.isEmpty() && home.isEmpty();
    }

    @Override
    public Set<Entry<Node.OperationId, Node.Operation>> entrySet() {
        return snapshot().entrySet();
    }

    @Override
    public Set<Node.OperationId> keySet() {
        return snapshot().keySet();
    }

    @Override
    public Collection<Node.Operation> values() {
        return snapshot().values();
    }

    @Override
    public Comparator<? super Node.OperationId> comparator() {
        return null;
    }

    @Override
    public Node.OperationId firstKey() {
        return snapshot().firstKey();
    }

    @Override
    public Node.OperationId lastKey() {
        return snapshot().lastKey();
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> firstEntry() {
        return snapshot().firstEntry();
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> lastEntry() {
        return snapshot().lastEntry();
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> pollFirstEntry() {
        var entry = firstEntry();
        if (entry != null)
            remove(entry.getKey());
        return entry;
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> pollLastEntry() {
        var entry = lastEntry();
        if (entry != null)
            remove(entry.getKey());
        return entry;
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> lowerEntry(Node.OperationId key) {
        return snapshot().lowerEntry(key);
    }

    @Override
    public Node.OperationId lowerKey(Node.OperationId key) {
        return snapshot().lowerKey(key);
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> floorEntry(Node.OperationId key) {
        return snapshot().floorEntry(key);
    }

    @Override
    public Node.OperationId floorKey(Node.OperationId key) {
        return snapshot().floorKey(key);
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> ceilingEntry(Node.OperationId key) {
        return snapshot().ceilingEntry(key);
    }

    @Override
    public Node.OperationId ceilingKey(Node.OperationId key) {
        return snapshot().ceilingKey(key);
    }

    @Override
    public Entry<Node.OperationId, Node.Operation> higherEntry(Node.OperationId key) {
        return snapshot().higherEntry(key);
    }

    @Override
    public Node.OperationId higherKey(Node.OperationId key) {
        return snapshot().higherKey(key);
    }

    @Override
    public NavigableMap<Node.OperationId, Node.Operation> descendingMap() {
        return snapshot().descendingMap();
    }

    @Override
    public NavigableSet<Node.OperationId> navigableKeySet() {
        return snapshot().navigableKeySet();
    }

    @Override
    public NavigableSet<Node.OperationId> descendingKeySet() {
        return snapshot().descendingKeySet();
    }

    @Override
    public NavigableMap<Node.OperationId, Node.Operation> subMap(Node.OperationId fromKey,
            boolean fromInclusive, Node.OperationId toKey, boolean toInclusive) {
        return snapshot().subMap(fromKey, fromInclusive, toKey, toInclusive);
    }

    @Override
    public NavigableMap<Node.OperationId, Node.Operation> headMap(Node.OperationId toKey,
            boolean inclusive) {
        return snapshot().headMap(toKey, inclusive);
    }

    @Override
    public NavigableMap<Node.OperationId, Node.Operation> tailMap(Node.OperationId fromKey,
            boolean inclusive) {
        return snapshot().tailMap(fromKey, inclusive);
    }

    @Override
    public SortedMap<Node.OperationId, Node.Operation> subMap(Node.OperationId fromKey,
            Node.OperationId toKey) {
        return snapshot().subMap(fromKey, toKey);
    }

    @Override
    public SortedMap<Node.OperationId, Node.Operation> headMap(Node.OperationId toKey) {
        return snapshot().headMap(toKey);
    }

    @Override
    public SortedMap<Node.OperationId, Node.Operation> tailMap(Node.OperationId fromKey) {
        return snapshot().tailMap(fromKey);
    }

    @Override
    public Node.Operation getOrDefault(Object key, Node.Operation defaultValue) {
        var value = get(key);
        return value == null ? defaultValue : value;
    }

    @Override
    public Node.Operation putIfAbsent(Node.OperationId key, Node.Operation value) {
        var existing = get(key);
        if (existing != null)
            return existing;
        put(key, value);
        return null;
    }

    @Override
    public boolean remove(Object key, Object value) {
        var existing = get(key);
        if (existing == null || !existing.equals(value))
            return false;
        remove(key);
        return true;
    }

    @Override
    public boolean replace(Node.OperationId key, Node.Operation oldValue, Node.Operation newValue) {
        var existing = get(key);
        if (existing == null || !existing.equals(oldValue))
            return false;
        put(key, newValue);
        return true;
    }

    @Override
    public Node.Operation replace(Node.OperationId key, Node.Operation value) {
        return containsKey(key) ? put(key, value) : null;
    }

    @Override
    public void forEach(BiConsumer<? super Node.OperationId, ? super Node.Operation> action) {
        snapshot().forEach(action);
    }

    @Override
    public void replaceAll(BiFunction<? super Node.OperationId, ? super Node.Operation, ? extends Node.Operation> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Node.Operation computeIfAbsent(Node.OperationId key,
            Function<? super Node.OperationId, ? extends Node.Operation> mappingFunction) {
        var existing = get(key);
        if (existing != null)
            return existing;
        var value = mappingFunction.apply(key);
        if (value != null)
            put(key, value);
        return value;
    }

    @Override
    public Node.Operation computeIfPresent(Node.OperationId key,
            BiFunction<? super Node.OperationId, ? super Node.Operation, ? extends Node.Operation> remappingFunction) {
        var existing = get(key);
        if (existing == null)
            return null;
        var value = remappingFunction.apply(key, existing);
        if (value == null)
            remove(key);
        else
            put(key, value);
        return value;
    }

    @Override
    public Node.Operation compute(Node.OperationId key,
            BiFunction<? super Node.OperationId, ? super Node.Operation, ? extends Node.Operation> remappingFunction) {
        var value = remappingFunction.apply(key, get(key));
        if (value == null)
            remove(key);
        else
            put(key, value);
        return value;
    }

    @Override
    public Node.Operation merge(Node.OperationId key, Node.Operation value,
            BiFunction<? super Node.Operation, ? super Node.Operation, ? extends Node.Operation> remappingFunction) {
        var existing = get(key);
        var merged = existing == null ? value : remappingFunction.apply(existing, value);
        if (merged == null)
            remove(key);
        else
            put(key, merged);
        return merged;
    }
}
