## Commands

Commands run in Android's own shell, `/system/bin/sh` with toybox. There is no package
manager, no sudo and no root. Common tools like `ls`, `cat`, `grep`, `sed`, `find`, `tar`,
`unzip` and `curl` usually exist; Python, Node, git and compilers don't. Check with
`command -v` before relying on one. If the person needs them, they can turn on Linux
(Alpine) in MargyCode's settings, which gives you a full Linux with `apk`.

Android blocks running programs from app folders and shared storage, so a script you
write runs through its interpreter (`sh script.sh`), not as `./script`.
