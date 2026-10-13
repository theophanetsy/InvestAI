#!/usr/bin/env bash
# Prepares a Linux environment (cloud agent sandbox, devcontainer, fresh VM) to build and test InvestAI.
# Idempotent, no sudo required: tools go to ~/.local. Every download is verified against its SHA-256.
#
# Usage: bash scripts/setup-dev-env.sh [--skip-browsers] [--skip-warmup]
#   --skip-browsers  do not install Chromium for Playwright (e2e tests)
#   --skip-warmup    do not pre-download Maven dependencies (needs network later)
#
# Afterwards, in a new shell (or `source ~/.investai-env`): see docs/agent-environments.md.
set -euo pipefail

JAVA_MAJOR=25
NODE_MAJOR=24
GITLEAKS_VERSION=8.30.1

SKIP_BROWSERS=false
SKIP_WARMUP=false
for arg in "$@"; do
  case "$arg" in
    --skip-browsers) SKIP_BROWSERS=true ;;
    --skip-warmup) SKIP_WARMUP=true ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="$HOME/.local/share/investai-tools"
BIN_DIR="$HOME/.local/bin"
ENV_FILE="$HOME/.investai-env"
DOWNLOADS="$(mktemp -d)"
trap 'rm -rf "$DOWNLOADS"' EXIT
mkdir -p "$TOOLS_DIR" "$BIN_DIR"
# Reuse tools installed by a previous run, even when the calling shell did not load them.
# shellcheck source=/dev/null
if [[ -f "$ENV_FILE" ]]; then . "$ENV_FILE"; fi

log() { printf '\n==> %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }

case "$(uname -m)" in
  x86_64 | amd64) ARCH_JDK=x64; ARCH_NODE=x64; ARCH_GITLEAKS=x64 ;;
  aarch64 | arm64) ARCH_JDK=aarch64; ARCH_NODE=arm64; ARCH_GITLEAKS=arm64 ;;
  *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac
[[ "$(uname -s)" == "Linux" ]] || { echo "This script targets Linux (on Windows, see docs/setup.md)." >&2; exit 1; }

can_sudo() { [[ "$(id -u)" == 0 ]] || sudo -n true 2>/dev/null; }
as_root() { if [[ "$(id -u)" == 0 ]]; then "$@"; else sudo -n "$@"; fi; }

# download <url> <expected-sha256> <output-file>
download() {
  curl --fail --silent --show-error --location --retry 3 --output "$3" "$1"
  echo "$2  $3" | sha256sum --check --status || { echo "Checksum mismatch for $1" >&2; exit 1; }
}

ensure_prerequisites() {
  local missing=()
  for cmd in curl tar xz sha256sum git; do command -v "$cmd" >/dev/null || missing+=("$cmd"); done
  [[ ${#missing[@]} -eq 0 ]] && return
  if can_sudo && command -v apt-get >/dev/null; then
    log "Installing system prerequisites: ${missing[*]}"
    as_root apt-get update -qq
    as_root apt-get install -y -qq curl tar xz-utils coreutils git ca-certificates >/dev/null
  else
    echo "Missing commands: ${missing[*]} (install them, then rerun)" >&2
    exit 1
  fi
}

major_version_of() { "$1" "${@:2}" 2>&1 | grep -oE '[0-9]+' | head -1 || true; }

install_node() {
  if command -v node >/dev/null && [[ "$(node -p 'process.versions.node.split(".")[0]')" == "$NODE_MAJOR" ]]; then
    log "Node.js $(node --version) already installed"
    return
  fi
  log "Installing Node.js $NODE_MAJOR"
  local base="https://nodejs.org/dist/latest-v${NODE_MAJOR}.x"
  local line file sha
  line="$(curl --fail --silent --show-error "$base/SHASUMS256.txt" | grep -E " node-v[0-9.]+-linux-${ARCH_NODE}\.tar\.xz$")"
  sha="${line%% *}"
  file="${line##* }"
  download "$base/$file" "$sha" "$DOWNLOADS/$file"
  rm -rf "$TOOLS_DIR/node" && mkdir -p "$TOOLS_DIR/node"
  tar -xJf "$DOWNLOADS/$file" -C "$TOOLS_DIR/node" --strip-components=1
  export PATH="$TOOLS_DIR/node/bin:$PATH"
}

install_jdk() {
  if command -v java >/dev/null && [[ "$(major_version_of java -version)" == "$JAVA_MAJOR" ]]; then
    log "JDK $JAVA_MAJOR already installed"
    return
  fi
  log "Installing Eclipse Temurin JDK $JAVA_MAJOR"
  local api="https://api.adoptium.net/v3/assets/latest/${JAVA_MAJOR}/hotspot?architecture=${ARCH_JDK}&image_type=jdk&os=linux&vendor=eclipse"
  local link sha
  read -r link sha < <(curl --fail --silent --show-error "$api" |
    node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const p=JSON.parse(d)[0].binary.package;console.log(p.link,p.checksum)})')
  download "$link" "$sha" "$DOWNLOADS/jdk.tar.gz"
  rm -rf "$TOOLS_DIR/jdk" && mkdir -p "$TOOLS_DIR/jdk"
  tar -xzf "$DOWNLOADS/jdk.tar.gz" -C "$TOOLS_DIR/jdk" --strip-components=1
  export JAVA_HOME="$TOOLS_DIR/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
}

install_gitleaks() {
  if command -v gitleaks >/dev/null && [[ "$(gitleaks version)" == *"$GITLEAKS_VERSION"* ]]; then
    log "gitleaks $GITLEAKS_VERSION already installed"
    return
  fi
  log "Installing gitleaks $GITLEAKS_VERSION (required by the pre-commit hook)"
  local base="https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}"
  local file="gitleaks_${GITLEAKS_VERSION}_linux_${ARCH_GITLEAKS}.tar.gz"
  local sha
  sha="$(curl --fail --silent --show-error --location "$base/gitleaks_${GITLEAKS_VERSION}_checksums.txt" | grep " ${file}$" | cut -d' ' -f1)"
  download "$base/$file" "$sha" "$DOWNLOADS/$file"
  tar -xzf "$DOWNLOADS/$file" -C "$BIN_DIR" gitleaks
}

write_env_file() {
  log "Writing $ENV_FILE"
  {
    echo "# Generated by scripts/setup-dev-env.sh"
    echo "export PATH=\"$BIN_DIR:\$PATH\""
    if [[ -d "$TOOLS_DIR/node/bin" ]]; then echo "export PATH=\"$TOOLS_DIR/node/bin:\$PATH\""; fi
    if [[ -d "$TOOLS_DIR/jdk" ]]; then
      echo "export JAVA_HOME=\"$TOOLS_DIR/jdk\""
      echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\""
    fi
  } >"$ENV_FILE"
  for rc in "$HOME/.bashrc" "$HOME/.profile"; do
    touch "$rc"
    grep -qF '.investai-env' "$rc" || echo "[ -f \"$ENV_FILE\" ] && . \"$ENV_FILE\"" >>"$rc"
  done
  # Claude Code sessions persist environment variables written to CLAUDE_ENV_FILE.
  if [[ -n "${CLAUDE_ENV_FILE:-}" ]]; then cat "$ENV_FILE" >>"$CLAUDE_ENV_FILE"; fi
  # shellcheck source=/dev/null
  . "$ENV_FILE"
}

install_project_dependencies() {
  log "Installing repository tooling (Lefthook git hooks, commitlint)"
  (cd "$REPO_ROOT" && npm ci --no-audit --no-fund)
  log "Installing frontend dependencies"
  (cd "$REPO_ROOT/frontend" && npm ci --no-audit --no-fund)
}

install_browsers() {
  if $SKIP_BROWSERS; then return; fi
  log "Installing Chromium for Playwright"
  if can_sudo; then
    (cd "$REPO_ROOT/frontend" && npx playwright install --with-deps chromium)
  else
    (cd "$REPO_ROOT/frontend" && npx playwright install chromium)
    warn "no root access: Playwright system libraries not installed, e2e tests may fail"
  fi
}

warm_maven_cache() {
  if $SKIP_WARMUP; then return; fi
  log "Pre-downloading Maven dependencies and plugins (no tests)"
  (cd "$REPO_ROOT/backend" && ./mvnw -B -q -ntp -DskipTests -Djacoco.skip=true verify)
}

report_docker() {
  if docker info >/dev/null 2>&1; then
    DOCKER_STATUS="available: full suite with \`cd backend && ./mvnw verify\`"
  else
    DOCKER_STATUS="NOT available: run \`cd backend && ./mvnw -DskipITs verify\` and say so in the PR (CI runs the ITs)"
  fi
}

ensure_prerequisites
install_node
install_jdk
install_gitleaks
write_env_file
install_project_dependencies
install_browsers
warm_maven_cache
report_docker

log "Environment ready"
echo "  java     : $(java -version 2>&1 | head -1)"
echo "  node     : $(node --version)"
echo "  gitleaks : $(gitleaks version)"
echo "  docker   : $DOCKER_STATUS"
echo "Open a new shell or run: source $ENV_FILE"
