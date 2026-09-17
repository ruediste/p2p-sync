#!/bin/sh
set -e
dafny verify src/main/dafny/fileSystemTests.dfy --verification-time-limit=2 --verify-included-files