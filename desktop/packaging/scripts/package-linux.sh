#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"
BUILD_DIR="${ROOT_DIR}/build"
APP_NAME="PortablePrint"
ICON_DIR="${ROOT_DIR}/packaging/icons"
LINUXDEPLOY="linuxdeploy-x86_64.AppImage"

echo "==> Cleaning previous builds"
rm -rf "${BUILD_DIR}" "${DIST_DIR}"
mkdir -p "${DIST_DIR}"

echo "==> Running PyInstaller for Linux"
pyinstaller "${ROOT_DIR}/PortablePrint.spec" --distpath "${DIST_DIR}" --workpath "${BUILD_DIR}" --specpath "${BUILD_DIR}"

echo "==> Preparing AppDir"
APPDIR="${DIST_DIR}/${APP_NAME}.AppDir"
rm -rf "${APPDIR}"
mkdir -p "${APPDIR}/usr/bin" "${APPDIR}/usr/lib" "${APPDIR}/usr/share/applications" "${APPDIR}/usr/share/icons/hicolor/256x256/apps"

cp -f "${DIST_DIR}/${APP_NAME}/${APP_NAME}" "${APPDIR}/usr/bin/${APP_NAME}"
cp -R "${DIST_DIR}/${APP_NAME}/_internal" "${APPDIR}/usr/lib/_internal"
chmod +x "${APPDIR}/usr/bin/${APP_NAME}"

cat > "${APPDIR}/usr/share/applications/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=PortablePrint
Exec=${APP_NAME}
Icon=${APP_NAME}
Categories=Utility;Graphics;
EOF

if [ -f "${ICON_DIR}/${APP_NAME}.png" ]; then
  cp "${ICON_DIR}/${APP_NAME}.png" "${APPDIR}/usr/share/icons/hicolor/256x256/apps/${APP_NAME}.png"
fi

echo "==> Building AppImage"
if command -v "${LINUXDEPLOY}" >/dev/null 2>&1; then
  "${LINUXDEPLOY}" --appdir "${APPDIR}" --output appimage --desktop-file "${APPDIR}/usr/share/applications/${APP_NAME}.desktop" --icon-file "${ICON_DIR}/${APP_NAME}.png" || true
elif [ -x "${LINUXDEPLOY}" ]; then
  "${LINUXDEPLOY}" --appdir "${APPDIR}" --output appimage --desktop-file "${APPDIR}/usr/share/applications/${APP_NAME}.desktop" --icon-file "${ICON_DIR}/${APP_NAME}.png" || true
else
  echo "linuxdeploy not found; AppImage not built."
fi

echo "==> Building deb"
DEB_DIR="${DIST_DIR}/${APP_NAME}_deb"
mkdir -p "${DEB_DIR}/DEBIAN" "${DEB_DIR}/usr/bin"
cp -f "${DIST_DIR}/${APP_NAME}/${APP_NAME}" "${DEB_DIR}/usr/bin/${APP_NAME}"
cp -R "${DIST_DIR}/${APP_NAME}/_internal" "${DEB_DIR}/usr/lib/_internal"
cat > "${DEB_DIR}/DEBIAN/control" <<EOF
Package: portableprint
Version: 1.0.0
Section: utils
Priority: optional
Architecture: amd64
Maintainer: PortablePrint <support@portableprint.local>
Description: PortablePrint label designer
EOF
dpkg-deb --build "${DEB_DIR}" "${DIST_DIR}/${APP_NAME}_1.0.0_amd64.deb" >/dev/null 2>&1 || true

echo "==> Packaging complete: ${DIST_DIR}"
