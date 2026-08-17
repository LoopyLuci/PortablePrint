#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"
BUILD_DIR="${ROOT_DIR}/build"
APP_NAME="PortablePrint"
APP_BUNDLE="${DIST_DIR}/${APP_NAME}.app"
DMG_PATH="${DIST_DIR}/${APP_NAME}-macOS.dmg"
MACPORTS_APP="${ROOT_DIR}/packaging/macos/${APP_NAME}.app"

echo "==> Cleaning previous builds"
rm -rf "${BUILD_DIR}" "${DIST_DIR}"
mkdir -p "${DIST_DIR}"

echo "==> Running PyInstaller for macOS"
pyinstaller "${ROOT_DIR}/PortablePrint.spec" --distpath "${DIST_DIR}" --workpath "${BUILD_DIR}" --specpath "${BUILD_DIR}"

echo "==> Creating macOS app bundle"
if [ ! -d "${MACPORTS_APP}" ]; then
  echo "Error: ${MACPORTS_APP} not found. Create a minimal .app bundle first."
  exit 1
fi
cp -R "${MACPORTS_APP}" "${APP_BUNDLE}"
mkdir -p "${APP_BUNDLE}/Contents/Resources"
cp -f "${DIST_DIR}/${APP_NAME}/${APP_NAME}" "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}"
cp -R "${DIST_DIR}/${APP_NAME}/_internal" "${APP_BUNDLE}/Contents/Resources/_internal"
chmod +x "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}"

echo "==> Building DMG"
if command -v hdiutil >/dev/null 2>&1; then
  STAGING="$(mktemp -d)"
  cp -R "${APP_BUNDLE}" "${STAGING}/"
  ln -s /Applications "${STAGING}/Applications"
  hdiutil create -volname "${APP_NAME}" -srcfolder "${STAGING}" -ov -format UDZO "${DMG_PATH}"
  rm -rf "${STAGING}"
  echo "==> DMG created: ${DMG_PATH}"
else
  echo "hdiutil not available; skipping DMG creation"
fi

echo "==> Packaging complete: ${APP_BUNDLE}"
