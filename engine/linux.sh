#!/system/bin/sh
# The agent's shell: Alpine Linux from the app's files, run through proot (fake root,
# hard links turned into symlinks, which Android forbids). The phone's own folders are
# bound at the same paths, so the working directory means the same inside and out.
export PROOT_LOADER="$MARGY_PROOT_LOADER" PROOT_TMP_DIR="$MARGY_PROOT_TMP"
# PRoot resolves -w before applying binds. Create the guest path before entering it,
# otherwise it warns and silently falls back to /.
mkdir -p "$MARGY_ROOTFS${MARGY_CWD:-$PWD}"
exec "$MARGY_PROOT" --kill-on-exit -0 --link2symlink -r "$MARGY_ROOTFS" \
    -b /dev -b /proc -b /sys -b /system -b /apex -b /storage -b /sdcard \
    -b "$MARGY_FILES" -b "$MARGY_CACHE" -w "${MARGY_CWD:-$PWD}" \
    /usr/bin/env -u LD_LIBRARY_PATH -u LD_PRELOAD \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin \
    HOME=/root TMPDIR=/tmp LANG=C.UTF-8 TERM=xterm-256color SHELL=/bin/sh \
    /bin/sh "$@"
