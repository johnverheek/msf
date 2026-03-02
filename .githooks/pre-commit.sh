#!/bin/bash

# MSF pre-commit hook
# Runs a build and test pass before committing

set -e

echo "Running pre-commit checks..."

# Check for trailing whitespace in staged files
if git diff --cached --check; then
    echo "✓ No trailing whitespace"
else
    echo "✗ Trailing whitespace detected — fix before committing"
    exit 1
fi

# Build and test
echo "Building and testing..."
./gradlew :msf-core:build

echo "✓ Pre-commit checks passed"
exit 0
