package com.github.ruediste.p2psync.node;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class NodeAlgorithmTest {

    private static class Node {
        long clock; // lamportClock
        // getOperations()
        // getOperationData()
    }

    private static class Operation {
        private static final AtomicLong nextToken = new AtomicLong();
        long token;
        boolean hasLargeData;

        public Operation(boolean hasLargeData) {
            this.token = nextToken.getAndIncrement();
            this.hasLargeData = hasLargeData;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Operation other = (Operation) obj;
            return token == other.token;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(token);
        }
    }

    private static class Snapshot {
        private List<Operation> operations;

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            Snapshot other = (Snapshot) obj;
            return operations.equals(other.operations);
        }

        @Override
        public int hashCode() {
            return operations.hashCode();
        }
    }

    @Test
    public void testAlgorithm() {
        // TODO: Implement test
    }
}
