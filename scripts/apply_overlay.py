from pathlib import Path

root = Path("source")
overlay = Path("overlays/ImportSystemPanel.kt")
target = root / "app/src/main/java/com/lagradost/cloudstream3/ui/settings/ImportSystemPanel.kt"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(overlay.read_text())

worker_overlay = Path("overlays/CloudStreamImportWorker.kt")
worker_target = root / "app/src/main/java/com/lagradost/cloudstream3/ui/settings/CloudStreamImportWorker.kt"
worker_target.write_text(worker_overlay.read_text())

screen = root / "app/src/main/java/com/lagradost/cloudstream3/ui/settings/SettingsFragmentScreen.kt"
text = screen.read_text()
if "ImportSystemPanel()" not in text:
    marker = "SettingsSearch(textFieldState = textFieldState)"
    if marker not in text:
        raise SystemExit("Could not find SettingsSearch insertion point")
    text = text.replace(marker, marker + "\n                Spacer(modifier = Modifier.height(MaterialTheme.padding.small))\n                ImportSystemPanel()", 1)
    screen.write_text(text)
