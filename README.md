# PortablePrint

A Windows desktop app for connecting to Bluetooth label makers and designing/printing labels for home organization, kitchen storage, document filing, health reminders, and more.

## Features

- **Quick Mode**: Simple text-entry label printing.
- **Creative Mode**: Drag-and-drop text/images onto a label canvas.
- **Bluetooth Connectivity**: BLE discovery + RFCOMM printing path.
- **Label Rendering**: Pillow-based preview and bitmap generation.
- **Templates**: Save and load label designs as YAML.
- **Print Queue**: Sequential printing with status feedback.
- **Packaging**: PyInstaller-built Windows executable.

## Supported Printers

Designed for Phomemo-style Bluetooth label printers, including D30, P12, M110 series, and compatible ESC/POS raster devices.

## Getting Started

```bash
python -m src.main
```

Or use the packaged executable in `dist/PortablePrint/`.

## Development

Built with Python, tkinter/ttkbootstrap, bleak, and Pillow.

## License

MIT
