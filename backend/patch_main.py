import datetime
import py_compile
import shutil
import sys

path = "main.py"
text = open(path, encoding="utf-8").read()

if "install_push" in text:
    print("main.py already contains install_push. Nothing to change.")
    sys.exit(0)

backup = path + ".bak-" + datetime.datetime.now().strftime("%H%M%S")
shutil.copyfile(path, backup)

out = []
placed = False
for line in text.split("\n"):
    out.append(line)
    if not placed and line.strip() == "from papers import install_papers":
        out.append("from push import install_push")
        placed = True

tail = "\n"
if not placed:
    tail += "from push import install_push\n"
tail += "install_push(app, sys.modules[__name__])\n"
tail += "Base.metadata.create_all(bind=engine)\n"

open(path, "w", encoding="utf-8").write("\n".join(out).rstrip() + "\n" + tail)

try:
    py_compile.compile(path, doraise=True)
    print("Done. Backup saved as", backup)
    print("The import was placed " + ("next to the other imports." if placed else "at the end of the file."))
except py_compile.PyCompileError as exc:
    shutil.copyfile(backup, path)
    print("The edit did not compile, so main.py was restored from the backup.")
    print(exc)
    sys.exit(1)
