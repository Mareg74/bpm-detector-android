# shellcheck shell=bash
# Source from pilot/install scripts: finds JDK 17+ on macOS (Gradle needs JAVA_HOME).
ensure_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    return 0
  fi
  if [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  for ver in 21 17; do
    local jh
    jh="$(/usr/libexec/java_home -v "$ver" 2>/dev/null || true)"
    if [[ -n "$jh" && -x "$jh/bin/java" ]]; then
      export JAVA_HOME="$jh"
      return 0
    fi
  done
    jh="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [[ -n "$jh" && -x "$jh/bin/java" ]]; then
      export JAVA_HOME="$jh"
      return 0
    fi
  fi
  local studio_jbr="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -x "$studio_jbr/bin/java" ]]; then
    export JAVA_HOME="$studio_jbr"
    return 0
  fi
  studio_jbr="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -x "$studio_jbr/bin/java" ]]; then
    export JAVA_HOME="$studio_jbr"
    return 0
  fi
  if command -v brew >/dev/null 2>&1; then
    local brew_java
    brew_java="$(brew --prefix openjdk@17 2>/dev/null || true)"
    if [[ -n "$brew_java" && -x "$brew_java/libexec/openjdk.jdk/Contents/Home/bin/java" ]]; then
      export JAVA_HOME="$brew_java/libexec/openjdk.jdk/Contents/Home"
      return 0
    fi
  fi
  cat >&2 <<'EOF'
No Java 17+ found for Gradle.

macOS:
  brew install openjdk@17
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
Or use Android Studio → bundled JBR (re-run after Studio install).
EOF
  return 1
}