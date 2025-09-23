#!/usr/bin/env bash
set -euo pipefail

# يتوقع متغير بيئي GOOGLE_SERVICES_JSON_BASE64 يحوي الملف بصيغة base64
if [[ -z "${GOOGLE_SERVICES_JSON_BASE64:-}" ]]; then
  echo "Environment variable GOOGLE_SERVICES_JSON_BASE64 is not set"
  exit 1
fi

# إنشاء المجلدات إذا لزم
mkdir -p app/src/debug
mkdir -p app/src/release

# فك الترميز وكتابة الملفين
echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > app/src/debug/google-services.json
cp app/src/debug/google-services.json app/src/release/google-services.json

echo "google-services.json has been written to debug/ and release/ directories."
