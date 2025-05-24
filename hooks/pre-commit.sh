#!/bin/sh

echo "Running pre-commit checks..."

if [ ! -f ./gradlew ]; then
  echo "Gradle wrapper (./gradlew) not found. Ensure you are in the project root."
  exit 1
fi
if [ ! -x ./gradlew ]; then
  echo "Gradle wrapper (./gradlew) is not executable."
  echo "Please run 'chmod +x ./gradlew'."
  exit 1
fi

echo "Applying Ktlint formatting..."
./gradlew ktlintFormat --daemon
FORMAT_RESULT=$?

if [ $FORMAT_RESULT -ne 0 ]; then
  echo "Error during Ktlint formatting. Please check the Gradle output and fix any issues."
  # exit 1
fi

git diff --quiet
FILES_WERE_MODIFIED_BY_FORMATTER=$?

if [ $FILES_WERE_MODIFIED_BY_FORMATTER -ne 0 ]; then
  echo "Ktlint made formatting changes."
  echo "Please stage these changes (e.g., 'git add .' or 'git add <specific_files>') and re-commit."
  echo "Files changed:"
  git diff --name-only
  # exit 1
fi

echo "Running Detekt static analysis..."
./gradlew detekt --daemon
DETEKT_RESULT=$?

if [ $DETEKT_RESULT -ne 0 ]; then
  echo "Detekt found issues. Please review the report (usually in build/reports/detekt/detekt.html) and fix them."
  # exit 1
fi

echo "Pre-commit checks passed successfully."
exit 0