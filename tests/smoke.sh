#!/bin/sh
set -eu
exe="$(pwd)/blckchat"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT HUP INT TERM
cd "$dir"
"$exe" init demo
"$exe" send demo alice 'hello | world'
"$exe" send demo bob 'reply'
"$exe" read demo > output.txt
grep -F 'alice: hello | world' output.txt
grep -F 'bob: reply' output.txt
if "$exe" init '../unsafe'; then exit 1; fi
if "$exe" send demo 'bad/name' test; then exit 1; fi
echo 'PASS'
