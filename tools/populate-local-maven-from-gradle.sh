#!/usr/bin/env bash
# 从本机 Gradle 缓存填充 local-maven/（DJI 官方 Maven 在 CI 上不可达）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
VER=5.17.0

copy_artifact() {
  local group_id="$1" artifact="$2" ext="$3"
  local maven_dir="${group_id//.//}"
  local dir="$ROOT/local-maven/$maven_dir/$artifact/$VER"
  mkdir -p "$dir"
  local found
  found="$(find "$CACHE/$group_id/$artifact/$VER" -name "*.$ext" -type f | head -1)"
  if [[ -z "$found" ]]; then
    echo "missing in gradle cache: $group_id:$artifact:$VER ($ext)" >&2
    exit 1
  fi
  cp -f "$found" "$dir/$artifact-$VER.$ext"
  # Preserve the official POM: generating a minimal POM silently removes runtime dependencies.
  local found_pom
  found_pom="$(find "$CACHE/$group_id/$artifact/$VER" -name "$artifact-$VER.pom" -type f | head -1)"
  if [[ -z "$found_pom" ]]; then
    echo "missing original POM in gradle cache: $group_id:$artifact:$VER" >&2
    exit 1
  fi
  cp -f "$found_pom" "$dir/$artifact-$VER.pom"
  echo "ok $group_id:$artifact:$VER"
}

copy_artifact com.dji dji-sdk-v5-aircraft aar
copy_artifact com.dji dji-sdk-v5-networkImp aar
copy_artifact com.dji dji-sdk-v5-aircraft-provided jar

echo "local-maven ready under $ROOT/local-maven"
