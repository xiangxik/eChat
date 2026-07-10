#!/bin/sh
set -eu

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

api_base_url=$(json_escape "${VITE_API_BASE_URL:-}")
chat_web_base_url=$(json_escape "${VITE_CHAT_WEB_BASE_URL:-}")

cat > /usr/share/nginx/html/env.js <<EOF
window.__ECHAT_ENV__ = {
  VITE_API_BASE_URL: "${api_base_url}",
  VITE_CHAT_WEB_BASE_URL: "${chat_web_base_url}"
};
EOF
