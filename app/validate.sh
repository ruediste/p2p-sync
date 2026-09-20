#!/bin/sh
set -e
# dafny verify src/main/dafny/fileSystem.dfy --verification-time-limit=2 --verify-included-files
dafny verify src/main/dafny/eventSetTests.dfy --verification-time-limit=10