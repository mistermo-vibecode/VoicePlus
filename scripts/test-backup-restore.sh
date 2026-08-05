#!/bin/bash
set -euo pipefail

package_name=${1:-com.github.mistermo_vibecode.voiceplus.debug}
temp_dir=$(mktemp -d)
original_transport=$(adb shell bmgr list transports | sed -n 's/^[[:space:]]*\* //p' | tr -d '\r')

cleanup() {
  if [[ -n "$original_transport" ]]; then
    adb shell bmgr transport "$original_transport" >/dev/null || true
  fi
  rm -rf "$temp_dir"
}
trap cleanup EXIT

echo "WARNING: this test will uninstall and reinstall $package_name"

adb shell bmgr enable true
adb shell bmgr transport com.android.localtransport/.LocalTransport | grep -q "Selected transport"
adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true'
adb shell bmgr backupnow "$package_name" | grep -F "Package $package_name with result: Success"

apk_files=()
while IFS= read -r apk_line; do
  apk_path=${apk_line#package:}
  apk_file="$temp_dir/$(basename "$apk_path")"
  adb pull "$apk_path" "$apk_file"
  apk_files+=("$apk_file")
done < <(adb shell pm path "$package_name" | tr -d '\r')

if [[ ${#apk_files[@]} -eq 0 ]]; then
  echo "No installed APKs found for $package_name" >&2
  exit 1
fi

adb shell pm uninstall --user 0 "$package_name"
adb install-multiple -t --user 0 "${apk_files[@]}"

echo "Restore install completed for $package_name"
