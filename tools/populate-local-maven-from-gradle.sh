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
  if [[ ! -f "$dir/$artifact-$VER.pom" ]]; then
    cat >"$dir/$artifact-$VER.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group_id</groupId>
  <artifactId>$artifact</artifactId>
  <version>$VER</version>
  <packaging>$ext</packaging>
</project>
EOF
  fi
  echo "ok $group_id:$artifact:$VER"
}

copy_artifact com.dji dji-sdk-v5-aircraft aar
copy_artifact com.dji dji-sdk-v5-networkImp aar
copy_artifact com.dji dji-sdk-v5-aircraft-provided jar

echo "local-maven ready under $ROOT/local-maven"
