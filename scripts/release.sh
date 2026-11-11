#!/usr/bin/env bash
# release.sh — executa T1..T9 do UPDATE.md (publicacao de versao do TempMonitor).
# Uso: scripts/release.sh --dry-run         # valida T1..T5, nao publica
#      scripts/release.sh                  # bump de patch + publica
#      scripts/release.sh 1.2.0            # versao explicita + publica
set -euo pipefail

OWNER="spigknot"
REPO="TempMonitor"
GRADLE_FILE="app/build.gradle.kts"
DIST="dist"
DRY=0
NEXT_VERSION=""

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY=1 ;;
    -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
    -*) echo "opcao desconhecida: $arg" >&2; exit 2 ;;
    *) NEXT_VERSION="$arg" ;;
  esac
done

log()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  ok: %s\n' "$*"; }
die()  { printf '\n\033[31mERRO: %s\033[0m\n' "$*" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"
[ -f "$GRADLE_FILE" ] || die "$GRADLE_FILE nao encontrado"

# ---------------------------------------------------------------- T1
log "T1 — repositorio limpo e sincronizado"
git fetch --quiet origin
[ -z "$(git status --porcelain)" ] || die "arvore suja: commite (nunca --force, nunca --no-verify)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[ "$BRANCH" = "main" ] || die "branch atual e '$BRANCH'; a release sai da main"
LOCAL="$(git rev-parse HEAD)"; REMOTE="$(git rev-parse origin/main)"
[ "$LOCAL" = "$REMOTE" ] || die "main local ($LOCAL) difere de origin/main ($REMOTE)"
ok "main = origin/main ($LOCAL)"

# ---------------------------------------------------------------- T2
log "T2 — testes unitarios"
bash gradlew :app:testDebugUnitTest --no-daemon --quiet
grep -h -oE '(failures|errors)="[0-9]+"' app/build/test-results/testDebugUnitTest/*.xml \
  | grep -v '="0"' && die "houve falha nos testes unitarios"
ok "testes verdes"

# ---------------------------------------------------------------- T3
log "T3 — incrementar versionCode/versionName"
CUR_VC=$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | head -n1 | grep -oE '[0-9]+')
CUR_VN=$(grep -oE 'versionName = "[0-9]+\.[0-9]+\.[0-9]+"' "$GRADLE_FILE" | head -n1 | tr -d 'versionName = "')
[ -n "$CUR_VC" ] && [ -n "$CUR_VN" ] || die "nao consegui ler versao atual em $GRADLE_FILE"
NEW_VC=$((CUR_VC + 1))
if [ -z "$NEXT_VERSION" ]; then
  MAJOR=$(echo "$CUR_VN" | cut -d. -f1)
  MINOR=$(echo "$CUR_VN" | cut -d. -f2)
  PATCH=$(echo "$CUR_VN" | cut -d. -f3)
  NEXT_VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
fi
echo "$NEXT_VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$' || die "versao invalida: $NEXT_VERSION (use X.Y.Z)"
[ "$NEW_VC" -gt "$CUR_VC" ] || die "versionCode nao aumentou"
TAG="v$NEXT_VERSION"
if [ "$DRY" = "1" ]; then
  ok "planejado: $CUR_VN ($CUR_VC) -> $NEXT_VERSION ($NEW_VC); tag $TAG"
else
  sed -i "s/versionCode = $CUR_VC/versionCode = $NEW_VC/" "$GRADLE_FILE"
  sed -i "s/versionName = \"$CUR_VN\"/versionName = \"$NEXT_VERSION\"/" "$GRADLE_FILE"
  ok "$CUR_VN ($CUR_VC) -> $NEXT_VERSION ($NEW_VC)"
fi

# Em dry-run nada e alterado: o APK sai com a versao ATUAL, nao com a planejada.
if [ "$DRY" = "1" ]; then EXPECT_VC=$CUR_VC; EXPECT_VN=$CUR_VN; else EXPECT_VC=$NEW_VC; EXPECT_VN=$NEXT_VERSION; fi

# ---------------------------------------------------------------- T4
log "T4 — build de release (R8 ligado)"
bash gradlew :app:assembleRelease --no-daemon --quiet
APK_SRC="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK_SRC" ] || die "APK de release nao gerado"
mkdir -p "$DIST"
APK="$DIST/TempMonitor-$EXPECT_VN.apk"
cp "$APK_SRC" "$APK"
ok "$APK ($(wc -c < "$APK") bytes)"

# ---------------------------------------------------------------- T5
log "T5 — conferir APK (versao + assinatura)"
BADGING=$(aapt2 dump badging "$APK")
echo "$BADGING" | grep -q "versionCode='$EXPECT_VC'" || die "versionCode no APK difere de $EXPECT_VC"
echo "$BADGING" | grep -q "versionName='$EXPECT_VN'" || die "versionName no APK difere de $EXPECT_VN"
apksigner verify "$APK" || die "assinatura invalida"
CERT=$(apksigner verify --print-certs "$APK" | grep -m1 'certificate DN')
ok "versionCode=$EXPECT_VC versionName=$EXPECT_VN"
ok "$CERT"

if [ "$DRY" = "1" ]; then
  log "dry-run: validado T1..T5, nada foi commitado nem publicado"
  exit 0
fi

# ---------------------------------------------------------------- T6
log "T6 — commit e push"
git add -A
git commit -q -m "release: $NEXT_VERSION (versionCode $NEW_VC)"
git push -q origin main
ok "main -> $(git rev-parse --short HEAD)"

# ---------------------------------------------------------------- T7
log "T7 — tag $TAG"
git tag -a "$TAG" -m "TempMonitor $NEXT_VERSION (versionCode $NEW_VC)"
git push -q origin "$TAG"
ok "tag publicada"

# ---------------------------------------------------------------- T8
log "T8 — release com APK anexado"
LAST_TAG=$(git describe --tags --abbrev=0 "$TAG^" 2>/dev/null || true)
if [ -n "$LAST_TAG" ]; then RANGE="$LAST_TAG..$TAG"; else RANGE="HEAD"; fi
NOTES=$(git log --pretty='- %s' "$RANGE" | head -n 40)
gh release create "$TAG" "$APK" \
  --repo "$OWNER/$REPO" \
  --title "TempMonitor $NEXT_VERSION" \
  --notes "$NOTES" >/dev/null
ok "release $TAG criada com $(basename "$APK")"

# ---------------------------------------------------------------- T9
log "T9 — conferir download publico"
URL="https://github.com/$OWNER/$REPO/releases/download/$TAG/$(basename "$APK")"
HDRS=$(curl -sSIL "$URL")
CODE=$(printf '%s' "$HDRS" | head -n1 | awk '{print $2}')
LEN=$(printf '%s' "$HDRS" | grep -i '^content-length:' | tail -n1 | tr -dc '0-9')
LOCAL_LEN=$(wc -c < "$APK" | tr -d ' ')
[ "$CODE" = "200" ] || die "download retornou HTTP $CODE"
[ "$LEN" = "$LOCAL_LEN" ] || die "content-length $LEN difere do local $LOCAL_LEN"
ok "HTTP 200, $LEN bytes"
printf '\n\033[32mPUBLICADO: %s\033[0m\n' "$URL"
