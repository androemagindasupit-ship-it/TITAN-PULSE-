from pathlib import Path
p=Path("android/app/src/main/AndroidManifest.xml")
s=p.read_text(encoding="utf-8")
if "android.permission.POST_NOTIFICATIONS" not in s:
    idx=s.find(">")
    s=s[:idx+1]+"\n    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n    <uses-permission android:name=\"android.permission.INTERNET\" />"+s[idx+1:]
p.write_text(s,encoding="utf-8")
