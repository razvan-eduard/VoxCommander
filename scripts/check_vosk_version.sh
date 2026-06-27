#!/bin/bash

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m'

log_info() { printf "${GREEN}%s${NC}\n" "$1"; }
log_warn() { printf "${YELLOW}%s${NC}\n" "$1"; }
log_error() { printf "${RED}%s${NC}\n" "$1"; }
log_blue() { printf "${BLUE}%s${NC}\n" "$1"; }

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOML_FILE="$PROJECT_ROOT/gradle/libs.versions.toml"

# 1. Get current version from TOML file
CURRENT_VERSION=$(grep "^vosk =" "$TOML_FILE" | grep -oE "[0-9]+\.[0-9]+\.[0-9]+")

if [ -z "$CURRENT_VERSION" ]; then
    log_error "❌ Could not find Vosk version in libs.versions.toml"
    exit 1
fi

log_blue "🔍 Checking Vosk version (via JitPack)..."
echo "Current version: $CURRENT_VERSION"

# 2. Fetch latest version from JitPack (More up-to-date for Vosk)
# JitPack API returns versions for a GitHub repo
LATEST_VERSION=$(curl -s --connect-timeout 5 --max-time 10 https://jitpack.io/api/builds/com.github.alphacep/vosk-android/latest | grep -oE "[0-9]+\.[0-9]+\.[0-9]+")

if [ -z "$LATEST_VERSION" ]; then
    log_warn "⚠️ Could not reach JitPack API. Checking Maven fallback..."
    # Fallback to a wider search if JitPack fails
    LATEST_VERSION=$(curl -s --connect-timeout 5 --max-time 10 "https://search.maven.org/solrsearch/select?q=g:com.alphacephei+AND+a:vosk-android&rows=50&wt=json" \
        | grep -oE "[0-9]+\.[0-9]+\.[0-9]+" | sort -V | tail -1)
fi

# 3. Final Comparison
if [ -n "$LATEST_VERSION" ] && [ "$CURRENT_VERSION" != "$LATEST_VERSION" ]; then
    HIGHER_VERSION=$(printf "%s\n%s" "$CURRENT_VERSION" "$LATEST_VERSION" | sort -V | tail -1)

    if [ "$HIGHER_VERSION" == "$LATEST_VERSION" ]; then
        log_warn "🚀 UPDATE AVAILABLE: $CURRENT_VERSION -> $LATEST_VERSION"
        echo -e "\nTo update, modify your ${BLUE}gradle/libs.versions.toml${NC}:"
        grep -n "^vosk =" "$TOML_FILE" | sed 's/^/Line /'
        echo -e "Change to: ${GREEN}vosk = \"$LATEST_VERSION\"${NC}\n"
    else
        log_info "✅ Vosk is up to date ($CURRENT_VERSION)."
    fi
else
    log_info "✅ Vosk is up to date ($CURRENT_VERSION)."
fi
