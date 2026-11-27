## Commands

Commands run in Alpine Linux 3.22 (aarch64), inside the app through proot, as a fake
root. Install what you need with `apk add`, for example `python3 py3-pip nodejs npm git
build-base`; `pip install` wants `--break-system-packages` or a venv. Ask before large
installs, since the phone's storage and data may be limited.

- The phone's folders keep their real paths inside: the working directory, the app's
  files and `/storage/emulated/0` (when the app has access to all files).
- `/root` is Linux's own home; it lives inside the app, not in shared storage.
- There is no systemd, no kernel modules and no real root: services you start live only
  while the command runs, and anything needing raw devices or mounting won't work.
- proot makes programs slower than on a computer; prefer lighter tools when they do.
