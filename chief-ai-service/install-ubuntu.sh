#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="${1:-/opt/villagerai/chief-ai-service}"
SERVICE_NAME="${2:-villagerai-chief}"
SERVICE_USER="${3:-minecraft}"
SERVICE_GROUP="${4:-minecraft}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

echo "[1/7] Pruefe Python 3"
if ! command -v python3 >/dev/null 2>&1; then
    apt-get update
    apt-get install -y python3
fi

echo "[2/7] Pruefe Benutzer und Gruppe"
if ! getent group "${SERVICE_GROUP}" >/dev/null 2>&1; then
    groupadd --system "${SERVICE_GROUP}"
fi

if ! id -u "${SERVICE_USER}" >/dev/null 2>&1; then
    useradd --system --no-create-home --gid "${SERVICE_GROUP}" --shell /usr/sbin/nologin "${SERVICE_USER}"
fi

echo "[3/7] Lege Installationsverzeichnis an"
mkdir -p "${INSTALL_DIR}"

echo "[4/7] Kopiere Dienstdateien"
install -m 0644 "${SCRIPT_DIR}/server.py" "${INSTALL_DIR}/server.py"
if [[ -f "${INSTALL_DIR}/config.json" ]]; then
    install -m 0644 "${SCRIPT_DIR}/config.json" "${INSTALL_DIR}/config.json.new"
else
    install -m 0644 "${SCRIPT_DIR}/config.json" "${INSTALL_DIR}/config.json"
fi
install -m 0755 "${SCRIPT_DIR}/start.sh" "${INSTALL_DIR}/start.sh"

echo "[5/7] Setze Besitzrechte"
chown -R "${SERVICE_USER}:${SERVICE_GROUP}" "${INSTALL_DIR}"

echo "[6/7] Schreibe systemd-Unit"
cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=VillagerAI Chief HTTP Service
After=network.target

[Service]
Type=simple
WorkingDirectory=${INSTALL_DIR}
ExecStart=/usr/bin/python3 ${INSTALL_DIR}/server.py
Restart=on-failure
RestartSec=5
User=${SERVICE_USER}
Group=${SERVICE_GROUP}

[Install]
WantedBy=multi-user.target
EOF

echo "[7/7] Aktiviere und starte Dienst"
systemctl daemon-reload
systemctl enable "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"

echo
echo "Installation abgeschlossen."
echo "Dienststatus pruefen: sudo systemctl status ${SERVICE_NAME}"
echo "Health pruefen: curl http://127.0.0.1:8080/health"
if [[ -f "${INSTALL_DIR}/config.json.new" ]]; then
    echo "Hinweis: Vorhandene config.json wurde beibehalten, neue Vorlage liegt in ${INSTALL_DIR}/config.json.new"
fi