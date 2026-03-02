#!/bin/bash

# MSF Pre-commit Hook
# Runs checks before commit to maintain code quality

set -e

echo "Running pre-commit checks..."

# Check for trailing whitespace
echo "Checking for trailing whitespace..."
if git diff --cached --check; then
    echo "✓ No trailing whitespace"
else
    echo "✗ Trailing whitespace detected"
    exit 1
fi

# Run Gradle build and tests
echo "Building project..."
./gradlew clean build

echo "✓ All pre-commit checks passed"
exit 0
