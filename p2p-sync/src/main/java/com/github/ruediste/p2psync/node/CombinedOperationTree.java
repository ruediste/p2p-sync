package com.github.ruediste.p2psync.node;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.github.ruediste.p2psync.proto.Sync;

/** A lazy, ordered view over one or more persisted operation trees. */
final class CombinedOperationTree extends AbstractMap<Node.OperationId, Node.Operation> {
    private final OperationTree[] trees;

    CombinedOperationTree(OperationTree... trees) {
        this.trees = trees.clone();
    }

    static boolean isFileOperation(Node.Operation operation) {
        try {
            return switch (Sync.UserOperationProto.parseFrom(operation.payload).getKind()) {
                case SET_STATUS, CREATE_DIRECTORY, CREATE_FILE, WRITE_FILE, TRUNCATE_FILE, MOVE, RENAME,
                        DELETE ->
                    true;
                default -> false;
            };
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            return false;
        }
    }

    private OperationTree target(Node.Operation value) {
        if (trees.length == 1)
            return trees[0];
        if (trees.length != 2)
            throw new UnsupportedOperationException("This operation view is read-only");
        return isFileOperation(value) ? trees[1] : trees[0];
    }

    @Override
    public Node.Operation put(Node.OperationId key, Node.Operation value) {
        var previous = get(key);
        var destination = target(value);
        for (var tree : trees)
            if (tree != destination)
                tree.remove(key);
        destination.put(key, value);
        return previous;
    }

    @Override
    public Node.Operation remove(Object key) {
        if (!(key instanceof Node.OperationId id))
            return null;
        Node.Operation result = null;
        for (var tree : trees) {
            var removed = tree.get(id);
            if (removed != null) {
                if (result == null)
                    result = removed;
                tree.remove(id);
            }
        }
        return result;
    }

    @Override
    public void putAll(Map<? extends Node.OperationId, ? extends Node.Operation> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        for (var tree : trees)
            for (var operation : tree.operations())
                tree.remove(operation.id);
    }

    @Override
    public Node.Operation get(Object key) {
        if (!(key instanceof Node.OperationId id))
            return null;
        for (int i = trees.length - 1; i >= 0; i--) {
            var operation = trees[i].get(id);
            if (operation != null)
                return operation;
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof Node.OperationId id))
            return false;
        for (var tree : trees)
            if (tree.contains(id))
                return true;
        return false;
    }

    @Override
    public int size() {
        int result = 0;
        Node.OperationId last = null;
        Node.OperationId next;
        while ((next = nextIdAfter(last)) != null) {
            last = next;
            result++;
        }
        return result;
    }

    @Override
    public boolean isEmpty() {
        return nextIdAfter(null) == null;
    }

    @Override
    public Set<Node.OperationId> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Node.OperationId> iterator() {
                return new Iterator<>() {
                    private Node.OperationId last;

                    @Override
                    public boolean hasNext() {
                        return nextIdAfter(last) != null;
                    }

                    @Override
                    public Node.OperationId next() {
                        var next = nextIdAfter(last);
                        if (next == null)
                            throw new java.util.NoSuchElementException();
                        last = next;
                        return next;
                    }
                };
            }

            @Override
            public int size() {
                return CombinedOperationTree.this.size();
            }
        };
    }

    Entry<Node.OperationId, Node.Operation> firstEntry() {
        var iterator = operations().iterator();
        if (!iterator.hasNext())
            return null;
        var operation = iterator.next();
        return Map.entry(operation.id, operation);
    }

    Entry<Node.OperationId, Node.Operation> lastEntry() {
        Entry<Node.OperationId, Node.Operation> result = null;
        for (var entry : entrySet())
            result = entry;
        return result;
    }

    Node.OperationId lastKey() {
        Node.OperationId result = null;
        for (var tree : trees) {
            var candidate = tree.lastId();
            if (candidate != null && (result == null || candidate.compareTo(result) > 0))
                result = candidate;
        }
        if (result == null)
            throw new java.util.NoSuchElementException();
        return result;
    }

    long rootOffset() {
        if (trees.length != 1)
            throw new IllegalStateException("A combined operation view has no single root");
        return trees[0].rootOffset();
    }

    Node.OperationId nextIdAfter(Node.OperationId id) {
        Node.OperationId result = null;
        for (var tree : trees) {
            var candidate = tree.nextIdAfter(id);
            if (candidate != null && (result == null || candidate.compareTo(result) < 0))
                result = candidate;
        }
        return result;
    }

    Map<Node.OperationId, Node.Operation> headMap(Node.OperationId toKey, boolean inclusive) {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<Node.OperationId, Node.Operation>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<Node.OperationId, Node.Operation>> iterator() {
                        var source = CombinedOperationTree.this.entrySet().iterator();
                        return new Iterator<>() {
                            Entry<Node.OperationId, Node.Operation> next;

                            private void advance() {
                                while (next == null && source.hasNext()) {
                                    var candidate = source.next();
                                    int comparison = candidate.getKey().compareTo(toKey);
                                    if (comparison < 0 || (inclusive && comparison == 0))
                                        next = candidate;
                                    else
                                        break;
                                }
                            }

                            @Override
                            public boolean hasNext() {
                                advance();
                                return next != null;
                            }

                            @Override
                            public Entry<Node.OperationId, Node.Operation> next() {
                                advance();
                                var result = next;
                                next = null;
                                return result;
                            }
                        };
                    }

                    @Override
                    public int size() {
                        int result = 0;
                        var iterator = iterator();
                        while (iterator.hasNext()) {
                            iterator.next();
                            result++;
                        }
                        return result;
                    }
                };
            }
        };
    }

    private Iterable<Node.Operation> operations() {
        return () -> new Iterator<>() {
            private Node.OperationId last;

            @Override
            public boolean hasNext() {
                return nextId() != null;
            }

            @Override
            public Node.Operation next() {
                var id = nextId();
                if (id == null)
                    throw new java.util.NoSuchElementException();
                last = id;
                for (int i = trees.length - 1; i >= 0; i--) {
                    var operation = trees[i].get(id);
                    if (operation != null)
                        return operation;
                }
                throw new IllegalStateException("Operation disappeared during iteration");
            }

            private Node.OperationId nextId() {
                return CombinedOperationTree.this.nextIdAfter(last);
            }
        };
    }

    @Override
    public Set<Entry<Node.OperationId, Node.Operation>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<Node.OperationId, Node.Operation>> iterator() {
                var operations = CombinedOperationTree.this.operations().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return operations.hasNext();
                    }

                    @Override
                    public Entry<Node.OperationId, Node.Operation> next() {
                        var operation = operations.next();
                        return Map.entry(operation.id, operation);
                    }
                };
            }

            @Override
            public int size() {
                return CombinedOperationTree.this.size();
            }
        };
    }
}