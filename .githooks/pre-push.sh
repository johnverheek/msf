#!/bin/bash

# MSF Pre-push Hook
# Runs comprehensive checks before pushing to remote

set -e

echo "Running pre-push checks..."

# Run full test suite
echo "Running all tests..."
./gradlew test

# Check code style
echo "Checking code style..."
./gradlew checkstyleMain checkstyleTest

echo "✓ All pre-push checks passed"
exit 0
