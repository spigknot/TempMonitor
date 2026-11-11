#!/usr/bin/env bash
# Publica o TempMonitor no GitHub de forma idempotente.
# Uso: scripts/publish.sh [owner]   (default: dono do remote/gh)
set -euo pipefail

OWNER="${1:-spigknot}"
REPO="TempMonitor"
DESC="Monitor termico em tempo real para Android com root (Kotlin + Jetpack Compose)"

command -v gh >/dev/null 2>&1 || {
  echo "GitHub CLI ausente. Instale com: pkg install gh"; exit 1; }

if ! gh auth status >/dev/null 2>&1; then
  echo "Sem autenticacao. Rode uma vez: gh auth login"; exit 1
fi

cd "$(git rev-parse --show-toplevel)"

if gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "Repo $OWNER/$REPO ja existe; seguindo para o push."
else
  echo "Criando $OWNER/$REPO..."
  gh repo create "$OWNER/$REPO" --public --description "$DESC"
fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "https://github.com/$OWNER/$REPO.git"
else
  git remote add origin "https://github.com/$OWNER/$REPO.git"
fi

git push -u origin main
echo "OK: https://github.com/$OWNER/$REPO"
