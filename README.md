# PortablePrint

Cross-platform Bluetooth label printing.

## Repo layout

- `desktop/` — existing Windows desktop app, plus macOS/Linux variants
- `android/` — native Android app written in Kotlin
- `shared/` — common protocol constants and label model used by all clients

## Getting started

### Desktop
```bash
cd desktop
python -m src.main
```

### Android
Open `android/` in Android Studio and run the `app` module.

## Supported printers

Phomemo-style Bluetooth label printers and compatible ESC/POS raster devices.

## License

MIT
