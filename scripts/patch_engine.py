#!/usr/bin/env python3
"""Wires Azeroid into the pinned engine checkout.

Every edit asserts it matched, so an engine bump that moves things around
fails the build loudly instead of producing a half-patched APK.
"""
import re
import sys
from pathlib import Path

engine = Path(sys.argv[1])


def edit(path: Path, pattern: str, repl: str, count: int = 1, flags: int = re.S) -> None:
    text = path.read_text(encoding="utf-8")
    new, n = re.subn(pattern, repl, text, flags=flags)
    if n < count:
        sys.exit(f"patch_engine: pattern not found in {path}: {pattern!r}")
    path.write_text(new, encoding="utf-8")


manifest = engine / "app/src/main/AndroidManifest.xml"

# The engine's own launcher icons go away: Azeroid is the only entry point.
edit(manifest,
     r'(android:name="com\.winlator\.cmod\.MainActivity".*?)<intent-filter>\s*'
     r'<action android:name="android\.intent\.action\.MAIN"\s*/>\s*'
     r'<category android:name="android\.intent\.category\.LAUNCHER"\s*/>\s*</intent-filter>',
     r"\1")
edit(manifest,
     r'(<category android:name="com\.oculus\.intent\.category\.VR" />\s*)'
     r'<category android:name="android\.intent\.category\.LAUNCHER" />',
     r"\1")

# Branding.
edit(manifest, r'android:label="[^"]*"', 'android:label="Azeroid"')
edit(manifest, r'android:icon="@mipmap/ic_launcher"', 'android:icon="@mipmap/azeroid_ic_launcher"')
edit(manifest, r'android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@mipmap/azeroid_ic_launcher"')

# Provider authorities must be unique per app, or Azeroid could not be
# installed next to another Winlator build.
edit(manifest, r'android:authorities="com\.winlator\.cmod\.', 'android:authorities="${applicationId}.', count=2)

edit(manifest,
     r'(<uses-permission android:name="android\.permission\.INTERNET" />)',
     r'\1\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />')

azeroid_components = """
        <activity
            android:name="com.azeroid.app.AzeroidActivity"
            android:theme="@style/AppThemeFullscreen.Dark"
            android:screenOrientation="sensorLandscape"
            android:launchMode="singleTask"
            android:exported="true"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name="com.azeroid.app.update.InstallResultReceiver"
            android:exported="false" />
"""
edit(manifest, r'(<application\b[^>]*>)', r"\1" + azeroid_components.replace("\\", "\\\\"))

# Engine bug fix: SysVSharedMemory.delete() mutated the segment table without the lock that
# attach()/detach() hold. Under Battle.net's Chromium (lots of MIT-SHM churn) detach() then read
# SparseArray's internal DELETED marker -> ClassCastException on the X server thread -> app crash.
edit(engine / "app/src/main/java/com/winlator/cmod/sysvshm/SysVSharedMemory.java",
     r"public void delete\(int shmid\) \{\s*SHMemory shmemory = shmemories\.get\(shmid\);\s*"
     r"if \(shmemory != null\) \{(.*?)shmemories\.remove\(shmid\);\s*\}\s*\}",
     r"public void delete(int shmid) {\n        synchronized (shmemories) {\n"
     r"            SHMemory shmemory = shmemories.get(shmid);\n"
     r"            if (shmemory != null) {\1shmemories.remove(shmid);\n            }\n        }\n    }")

# Same class of race one level up: the X server thread and the renderer share this SparseArray.
shm_mgr = engine / "app/src/main/java/com/winlator/cmod/xserver/SHMSegmentManager.java"
for method in ("void attach(int xid, int shmid)", "void detach(int xid)", "ByteBuffer getData(int xid)"):
    edit(shm_mgr, r"public " + re.escape(method), "public synchronized " + method)

# Build config (applicationId, version, signing) lives in overlay/app/azeroid.gradle.
build_gradle = engine / "app/build.gradle"
build_gradle.write_text(build_gradle.read_text(encoding="utf-8") + "\napply from: 'azeroid.gradle'\n", encoding="utf-8")

edit(engine / "settings.gradle", r'rootProject\.name = "[^"]*"', 'rootProject.name = "Azeroid"')

print("patch_engine: ok")
