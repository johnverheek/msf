#!/bin/bash

# MSF pre-push hook
# Runs the full test suite before pushing

set -e

echo "Running pre-push checks..."

echo "Running all tests..."
./gradlew test

echo "✓ All tests passed — safe to push"
exit 0
