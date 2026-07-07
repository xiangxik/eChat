#!/bin/sh
set -eu

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

api_base_url=$(json_escape "${VITE_API_BASE_URL:-}")
chatbot_id=$(json_escape "${VITE_CHATBOT_ID:-1}")
chatbot_name=$(json_escape "${VITE_CHATBOT_NAME:-eChat Assistant}")

cat > /usr/share/nginx/html/env.js <<EOF
window.__ECHAT_ENV__ = {
  VITE_API_BASE_URL: "${api_base_url}",
  VITE_CHATBOT_ID: "${chatbot_id}",
  VITE_CHATBOT_NAME: "${chatbot_name}"
};
EOF
