#!/bin/zsh
# ============================================================
# Certificate preview — fast layout loop
# Generates both certificate PDFs and opens them.
# Usage:  ./preview-cert.sh
# ============================================================
set -e
cd "$(dirname "$0")"

echo "🔧 Rendering certificate previews…"
mvn -q -Dtest=CertificatePreviewTool test

ACH="target/cert-preview/achievement.pdf"
TYP="target/cert-preview/typing.pdf"

if [[ -f "$ACH" && -f "$TYP" ]]; then
  echo "📄 Opening previews…"
  open "$ACH" "$TYP" 2>/dev/null || echo "Open these files: $ACH  $TYP"
else
  echo "❌ Preview files not found — check the test output above."
  exit 1
fi
