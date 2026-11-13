#!/usr/bin/env bash
# Lance la compilation du mod et transforme les erreurs javac/Gradle en annotations GitHub.
set -o pipefail
DIR=${1:-mod}
cd "$DIR"
gradle build --no-daemon --stacktrace 2>&1 | tee ../gradle.log
code=${PIPESTATUS[0]}
cd ..
if [ "$code" -ne 0 ]; then
  grep -E '\.java:[0-9]+: error:' gradle.log | head -40 | while IFS= read -r line; do
    file=$(echo "$line" | sed -E "s#^.*(${DIR}/src/[^:]+\\.java):([0-9]+): error: (.*)\$#\\1#")
    ln=$(echo "$line" | sed -E 's#^.*\.java:([0-9]+): error: .*$#\1#')
    msg=$(echo "$line" | sed -E 's#^.*\.java:[0-9]+: error: (.*)$#\1#')
    echo "::error file=${file},line=${ln}::${msg}"
  done
  # Contexte (symboles introuvables, erreurs de dépendances…)
  grep -E -A3 'error:|FAILURE|What went wrong|Could not' gradle.log | head -80 | tr '\n' '|' | sed 's/%/%25/g' | cut -c1-6000 | sed 's/^/::error title=Gradle::/' ; echo
fi
exit $code
