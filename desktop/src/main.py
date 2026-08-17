import tkinter as tk
from tkinter import ttk, messagebox, filedialog, simpledialog, colorchooser
import ttkbootstrap as tb
from ttkbootstrap.constants import *
import bleak
import asyncio
from PIL import Image, ImageTk, ImageDraw, ImageFont
import yaml
import os
import threading
import time
import uuid
import math
import json
import datetime

from src.printer import print_text, print_image
from src.printer import PrintDensity, PrintSpeed, LabelMode


class LabelCanvas(tk.Canvas):
    def __init__(self, master, label_width_mm, label_height_mm, **kwargs):
        super().__init__(master, **kwargs)
        self.label_width_mm = label_width_mm
        self.label_height_mm = label_height_mm
        self.scale = 8  # pixels per mm
        self.config(
            width=label_width_mm * self.scale,
            height=label_height_mm * self.scale,
            bg="white",
            highlightthickness=1,
            highlightbackground="#cccccc",
        )
        self.items = []
        self.selected_item = None
        self.drag_data = {"x": 0, "y": 0, "item": None}
        self.resize_handle = None
        
        self.bind("<ButtonPress-1>", self.on_press)
        self.bind("<ButtonRelease-1>", self.on_release)
        self.bind("<B1-Motion>", self.on_drag)
        self.bind("<Delete>", self.on_delete)
        self.bind("<ButtonPress-3>", self.on_right_click)
        
        # Draw label border
        self.create_rectangle(
            1, 1,
            self.label_width_mm * self.scale - 1,
            self.label_height_mm * self.scale - 1,
            outline="#dddddd",
            tags="border"
        )
    
    def add_text(self, text="Text", x=None, y=None, font_name="Arial", font_size=24, fill="black"):
        if x is None:
            x = 10
        if y is None:
            y = 10
        item_id = self.create_text(x, y, text=text, anchor="nw", fill=fill, font=(font_name, font_size))
        self.items.append({
            "type": "text",
            "id": item_id,
            "text": text,
            "x": x,
            "y": y,
            "font_name": font_name,
            "font_size": font_size,
            "fill": fill,
        })
        self._attach_handles(item_id)
    
    def add_image(self, image_path, x=None, y=None, width=None, height=None):
        if x is None:
            x = 10
        if y is None:
            y = 10
        try:
            image = Image.open(image_path)
            if width is None or height is None:
                image.thumbnail((200, 200))
                photo = ImageTk.PhotoImage(image)
                width = photo.width()
                height = photo.height()
            else:
                image = image.resize((width, height))
                photo = ImageTk.PhotoImage(image)
            item_id = self.create_image(x, y, anchor="nw", image=photo)
            self.items.append({
                "type": "image",
                "id": item_id,
                "path": image_path,
                "x": x,
                "y": y,
                "width": width,
                "height": height,
                "photo": photo,
            })
            self._attach_handles(item_id)
        except Exception as e:
            messagebox.showerror("Image Error", str(e))
    
    def _attach_handles(self, item_id):
        self.addtag_withtag("movable", item_id)
    
    def on_press(self, event):
        self.drag_data["item"] = self.find_closest(event.x, event.y)[0]
        if "movable" in self.gettags(self.drag_data["item"]):
            self.drag_data["x"] = event.x
            self.drag_data["y"] = event.y
            self.selected_item = self.drag_data["item"]
            self.tag_raise(self.selected_item)
        else:
            self.selected_item = None
    
    def on_drag(self, event):
        if self.drag_data["item"] and "movable" in self.gettags(self.drag_data["item"]):
            dx = event.x - self.drag_data["x"]
            dy = event.y - self.drag_data["y"]
            self.move(self.drag_data["item"], dx, dy)
            self.drag_data["x"] = event.x
            self.drag_data["y"] = event.y
            self._update_item_data(self.drag_data["item"])
    
    def on_release(self, event):
        self.drag_data["item"] = None
    
    def on_delete(self, event):
        if self.selected_item:
            self.delete(self.selected_item)
            self.items = [item for item in self.items if item.get("id") != self.selected_item]
            self.selected_item = None
    
    def on_right_click(self, event):
        item = self.find_closest(event.x, event.y)[0]
        data = self._get_item_data(item)
        if not data:
            return
        menu = tk.Menu(self, tearoff=0)
        if data["type"] == "text":
            menu.add_command(label="Edit Text", command=lambda: self._edit_text(item, data))
            menu.add_command(label="Change Color", command=lambda: self._change_color(item, data))
        menu.add_command(label="Delete", command=lambda: self.on_delete(None))
        menu.tk_popup(event.x_root, event.y_root)
    
    def _get_item_data(self, item_id):
        for item in self.items:
            if item.get("id") == item_id:
                return item
        return None
    
    def _update_item_data(self, item_id):
        coords = self.coords(item_id)
        data = self._get_item_data(item_id)
        if data and coords:
            data["x"] = coords[0]
            data["y"] = coords[1]
    
    def _edit_text(self, item_id, data):
        new_text = simpledialog.askstring("Edit Text", "Enter text:", initialvalue=data.get("text", ""))
        if new_text is not None:
            self.itemconfig(item_id, text=new_text)
            data["text"] = new_text
    
    def _change_color(self, item_id, data):
        color = colorchooser.askcolor(color=data.get("fill", "black"))[1]
        if color:
            self.itemconfig(item_id, fill=color)
            data["fill"] = color
    
    def render_to_image(self, bg="white"):
        width = int(self.label_width_mm * self.scale)
        height = int(self.label_height_mm * self.scale)
        image = Image.new("RGB", (width, height), bg)
        draw = ImageDraw.Draw(image)
        
        for item in self.items:
            if item["type"] == "text":
                try:
                    font = ImageFont.truetype(item.get("font_name", "arial.ttf"), item.get("font_size", 24))
                except Exception:
                    font = ImageFont.load_default()
                draw.text((item["x"], item["y"]), item.get("text", ""), fill=item.get("fill", "black"), font=font)
            elif item["type"] == "image":
                try:
                    pil = Image.open(item["path"])
                    pil = pil.resize((item["width"], item["height"]))
                    image.paste(pil, (item["x"], item["y"]))
                except Exception:
                    pass
        return image

    def render_preview_image(self, text=None, bg="white"):
        width = int(self.label_width_mm * self.scale)
        height = int(self.label_height_mm * self.scale)
        image = Image.new("RGB", (width, height), bg)
        draw = ImageDraw.Draw(image)
        
        if text:
            try:
                font = ImageFont.truetype("arial.ttf", 24)
            except Exception:
                font = ImageFont.load_default()
            draw.text((10, 10), text, fill="black", font=font)
        else:
            for item in self.items:
                if item["type"] == "text":
                    try:
                        font = ImageFont.truetype(item.get("font_name", "arial.ttf"), item.get("font_size", 24))
                    except Exception:
                        font = ImageFont.load_default()
                    draw.text((item["x"], item["y"]), item.get("text", ""), fill=item.get("fill", "black"), font=font)
                elif item["type"] == "image":
                    try:
                        pil = Image.open(item["path"])
                        pil = pil.resize((item["width"], item["height"]))
                        image.paste(pil, (item["x"], item["y"]))
                    except Exception:
                        pass
        return image


class PrintMasterApp:
    def __init__(self, root):
        self.root = root
        self.root.title("PortablePrint")
        self.root.geometry("900x700")
        
        self.style = tb.Style(theme="flatly")
        
        self.bluetooth_devices = []
        self.selected_device = None
        self.client = None
        self.print_queue = []
        self.printing = False
        
        self.main_frame = ttk.Frame(root)
        self.main_frame.pack(fill=BOTH, expand=YES, padx=10, pady=10)
        
        self.notebook = ttk.Notebook(self.main_frame)
        self.notebook.pack(fill=BOTH, expand=YES)
        
        self.lite_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.lite_frame, text="Lite Mode")
        self.create_lite_mode()
        
        self.creative_frame = ttk.Frame(self.notebook)
        self.notebook.add(self.creative_frame, text="Creative Mode")
        self.create_creative_mode()
        
        self.status_var = tk.StringVar(value="Bluetooth: Not Connected")
        self.status_bar = ttk.Label(root, textvariable=self.status_var, relief=SUNKEN, anchor=W)
        self.status_bar.pack(side=BOTTOM, fill=X)
    
    def create_lite_mode(self):
        preview_frame = ttk.LabelFrame(self.lite_frame, text="Label Preview")
        preview_frame.pack(fill=X, padx=10, pady=5)
        
        self.preview_label = ttk.Label(preview_frame, text="Enter text to see preview", anchor=CENTER)
        self.preview_label.pack(fill=X, padx=10, pady=10)
        
        input_frame = ttk.LabelFrame(self.lite_frame, text="Label Text")
        input_frame.pack(fill=X, padx=10, pady=5)
        
        self.text_entry = ttk.Entry(input_frame, font=("Helvetica", 12))
        self.text_entry.pack(fill=X, padx=10, pady=10)
        self.text_entry.bind("<KeyRelease>", self.update_preview)
        
        button_frame = ttk.Frame(self.lite_frame)
        button_frame.pack(fill=X, padx=10, pady=10)
        
        self.print_btn = tb.Button(button_frame, text="Print Label", bootstyle=SUCCESS, command=self.print_label)
        self.print_btn.pack(side=RIGHT, padx=5)
        
        device_frame = ttk.LabelFrame(self.lite_frame, text="Bluetooth Device")
        device_frame.pack(fill=X, padx=10, pady=5)
        
        self.device_var = tk.StringVar()
        self.device_combo = ttk.Combobox(device_frame, textvariable=self.device_var, state="readonly")
        self.device_combo.pack(fill=X, padx=10, pady=5)
        self.device_combo.bind("<<ComboboxSelected>>", self.select_device)
        
        self.refresh_btn = tb.Button(device_frame, text="Refresh Devices", bootstyle=OUTLINE, command=self.discover_devices)
        self.refresh_btn.pack(side=RIGHT, padx=5, pady=5)
        
        manual_frame = ttk.Frame(device_frame)
        manual_frame.pack(fill=X, padx=10, pady=(0, 5))
        ttk.Label(manual_frame, text="Manual BT Address:").pack(side=LEFT)
        self.manual_address_var = tk.StringVar()
        self.manual_address_entry = ttk.Entry(manual_frame, textvariable=self.manual_address_var)
        self.manual_address_entry.pack(side=LEFT, fill=X, expand=YES, padx=5)
        self.manual_address_entry.bind("<Return>", lambda e: self.select_manual_address())
        
        channel_frame = ttk.Frame(device_frame)
        channel_frame.pack(fill=X, padx=10, pady=(0, 5))
        ttk.Label(channel_frame, text="RFCOMM Channel:").pack(side=LEFT)
        self.channel_var = tk.StringVar(value="1")
        self.channel_combo = ttk.Combobox(channel_frame, textvariable=self.channel_var, state="readonly", width=10, values=["1", "2", "3"])
        self.channel_combo.pack(side=LEFT, padx=5)
    
    def create_creative_mode(self):
        top_controls = ttk.Frame(self.creative_frame)
        top_controls.pack(fill=X, padx=10, pady=5)
        
        tb.Button(top_controls, text="Add Text", bootstyle=OUTLINE, command=self.creative_add_text).pack(side=LEFT, padx=5)
        tb.Button(top_controls, text="Add Image", bootstyle=OUTLINE, command=self.creative_add_image).pack(side=LEFT, padx=5)
        tb.Button(top_controls, text="Save Template", bootstyle=SECONDARY, command=self.save_template).pack(side=LEFT, padx=5)
        tb.Button(top_controls, text="Load Template", bootstyle=SECONDARY, command=self.load_template).pack(side=LEFT, padx=5)
        tb.Button(top_controls, text="Export JSON", bootstyle=OUTLINE, command=self.export_template_json).pack(side=LEFT, padx=5)
        tb.Button(top_controls, text="Import JSON", bootstyle=OUTLINE, command=self.import_template_json).pack(side=LEFT, padx=5)
        
        self.canvas_frame = ttk.Frame(self.creative_frame)
        self.canvas_frame.pack(fill=BOTH, expand=YES, padx=10, pady=5)
        
        self.canvas = LabelCanvas(self.canvas_frame, label_width_mm=50, label_height_mm=30)
        self.canvas.pack(fill=BOTH, expand=YES)
        
        bottom_frame = ttk.Frame(self.creative_frame)
        bottom_frame.pack(fill=X, padx=10, pady=5)
        
        self.creative_print_btn = tb.Button(bottom_frame, text="Print Label", bootstyle=SUCCESS, command=self.creative_print)
        self.creative_print_btn.pack(side=RIGHT)
        
        queue_frame = ttk.LabelFrame(bottom_frame, text="Print Queue")
        queue_frame.pack(side=RIGHT, fill=Y, padx=10)
        self.queue_listbox = tk.Listbox(queue_frame, height=5, width=50)
        self.queue_listbox.pack(fill=Y, padx=5, pady=5)
        qbtn_frame = ttk.Frame(queue_frame)
        qbtn_frame.pack(fill=X, padx=5, pady=5)
        tb.Button(qbtn_frame, text="Clear", bootstyle=DANGER, command=self.clear_queue).pack(side=RIGHT)
    
    def update_preview(self, event=None):
        text = self.text_entry.get()
        if text:
            self.preview_label.config(text=text)
        else:
            self.preview_label.config(text="Enter text to see preview")
    
    def discover_devices(self):
        self.status_var.set("Bluetooth: Scanning...")
        self.refresh_btn.config(state=tk.DISABLED)
        
        def run_discovery():
            try:
                loop = asyncio.new_event_loop()
                asyncio.set_event_loop(loop)
                devices = loop.run_until_complete(self._discover_devices_async())
                loop.close()
                self.root.after(0, self.update_device_list, devices)
            except Exception as e:
                self.root.after(0, lambda: self.status_var.set(f"Bluetooth: Scan failed - {e}"))
            finally:
                self.root.after(0, lambda: self.refresh_btn.config(state=tk.NORMAL))
        
        thread = threading.Thread(target=run_discovery)
        thread.daemon = True
        thread.start()
    
    async def _discover_devices_async(self):
        devices = []
        try:
            ble_devices = await asyncio.wait_for(bleak.BleakScanner.discover(), timeout=10.0)
            for device in ble_devices:
                devices.append({
                    "name": device.name or "Unknown",
                    "address": device.address,
                    "type": "BLE"
                })
        except asyncio.TimeoutError:
            pass
        except Exception as e:
            print(f"BLE discovery error: {e}")
        return devices
    
    def update_device_list(self, devices):
        self.bluetooth_devices = devices
        device_names = [f"{d['name']} ({d['address']})" for d in devices]
        self.device_combo['values'] = device_names
        if device_names:
            self.device_combo.current(0)
            self.select_device()
        self.status_var.set(f"Bluetooth: Found {len(devices)} device(s)")
    
    def select_device(self, event=None):
        selection = self.device_combo.current()
        if selection >= 0 and selection < len(self.bluetooth_devices):
            self.selected_device = self.bluetooth_devices[selection]
            self.status_var.set(f"Bluetooth: Selected {self.selected_device['name']}")
        else:
            self.selected_device = None
            self.status_var.set("Bluetooth: No device selected")
    
    def select_manual_address(self):
        address = self.manual_address_var.get().strip()
        if address:
            self.selected_device = {
                "name": "Manual",
                "address": address,
                "type": "Manual"
            }
            self.device_var.set("")
            self.status_var.set(f"Bluetooth: Manual address {address}")
    
    def _selected_channel(self) -> int:
        try:
            return int(self.channel_var.get())
        except Exception:
            return 1
    
    def print_label(self):
        if not self.selected_device:
            messagebox.showwarning("No Device", "Please select or enter a Bluetooth device/address first.")
            return
        
        text = self.text_entry.get()
        if not text:
            messagebox.showwarning("Empty Label", "Please enter label text first.")
            return
        
        self.print_queue.append(("text", text, self._selected_channel()))
        self.status_var.set(f"Queued: {text[:20]}...")
        self._update_queue_ui()
        self._process_queue()
    
    def _update_queue_ui(self):
        try:
            self.queue_listbox.delete(0, tk.END)
            for kind, payload, _ in self.print_queue:
                label = str(payload)
                if len(label) > 40:
                    label = label[:37] + "..."
                self.queue_listbox.insert(tk.END, f"{kind}: {label}")
        except Exception:
            pass
    
    def _process_queue(self):
        if self.printing or not self.print_queue or not self.selected_device:
            return
        self.printing = True
        kind, payload, channel = self.print_queue.pop(0)
        self._update_queue_ui()
        self.status_var.set(f"Printing: {str(payload)[:20]}...")
        
        def worker():
            try:
                if kind == "text":
                    print_text(self.selected_device["address"], channel, payload)
                elif kind == "image":
                    print_image(self.selected_device["address"], channel, payload)
                self.root.after(0, lambda: self.status_var.set("Printed"))
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("Print Error", str(e)))
                self.root.after(0, lambda: self.status_var.set("Print failed"))
            finally:
                self.printing = False
                self.root.after(0, self._process_queue)
        
        thread = threading.Thread(target=worker)
        thread.daemon = True
        thread.start()
    
    def clear_queue(self):
        self.print_queue.clear()
        self._update_queue_ui()
        self.status_var.set("Queue cleared")
    
    def creative_add_text(self):
        text = simpledialog.askstring("Add Text", "Enter text:", initialvalue="Text")
        if text:
            self.canvas.add_text(text=text)
    
    def creative_add_image(self):
        file_path = filedialog.askopenfilename(filetypes=[("Image files", "*.png *.jpg *.jpeg *.gif *.bmp")])
        if file_path:
            self.canvas.add_image(file_path)
    
    def creative_print(self):
        if not self.selected_device:
            messagebox.showwarning("No Device", "Please select or enter a Bluetooth device/address first.")
            return
        
        if not self.canvas.items:
            messagebox.showwarning("Empty Label", "Add some text or images before printing.")
            return
        
        try:
            channel = int(self.channel_var.get())
        except Exception:
            channel = 1
        
        self.status_var.set("Rendering label...")
        
        def do_print():
            try:
                image = self.canvas.render_to_image()
                tmp_path = os.path.join(os.environ.get("TEMP", "."), f"label_render_{uuid.uuid4().hex}.png")
                image.save(tmp_path)
                self.print_queue.append(("image", tmp_path, channel))
                self.status_var.set("Queued creative label")
                self._update_queue_ui()
                self.root.after(0, self._process_queue)
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("Render Error", str(e)))
                self.root.after(0, lambda: self.status_var.set("Render failed"))
        
        thread = threading.Thread(target=do_print)
        thread.daemon = True
        thread.start()
    
    def save_template(self):
        data = {
            "width_mm": self.canvas.label_width_mm,
            "height_mm": self.canvas.label_height_mm,
            "items": []
        }
        for item in self.canvas.items:
            entry = {k: v for k, v in item.items() if k in ("type", "text", "x", "y", "font_name", "font_size", "fill", "path", "width", "height")}
            data["items"].append(entry)
        
        file_path = filedialog.asksaveasfilename(defaultextension=".yaml", filetypes=[("YAML", "*.yaml"), ("All", "*.*")])
        if file_path:
            with open(file_path, "w") as f:
                yaml.safe_dump(data, f)
            self.status_var.set(f"Template saved: {os.path.basename(file_path)}")
    
    def load_template(self):
        file_path = filedialog.askopenfilename(filetypes=[("YAML", "*.yaml"), ("All", "*.*")])
        if not file_path:
            return
        try:
            with open(file_path, "r") as f:
                data = yaml.safe_load(f)
            for item in list(self.canvas.items):
                self.canvas.delete(item["id"])
            self.canvas.items = []
            
            for item in data.get("items", []):
                if item["type"] == "text":
                    self.canvas.add_text(
                        text=item.get("text", "Text"),
                        x=item.get("x", 10),
                        y=item.get("y", 10),
                        font_name=item.get("font_name", "Arial"),
                        font_size=item.get("font_size", 24),
                        fill=item.get("fill", "black"),
                    )
                elif item["type"] == "image":
                    self.canvas.add_image(
                        item.get("path", ""),
                        x=item.get("x", 10),
                        y=item.get("y", 10),
                        width=item.get("width", 100),
                        height=item.get("height", 100),
                    )
            self.status_var.set(f"Template loaded: {os.path.basename(file_path)}")
        except Exception as e:
            messagebox.showerror("Load Error", str(e))

    def export_template_json(self):
        data = {
            "version": 1,
            "exported_at": datetime.datetime.now().isoformat(),
            "width_mm": self.canvas.label_width_mm,
            "height_mm": self.canvas.label_height_mm,
            "items": []
        }
        for item in self.canvas.items:
            entry = {k: v for k, v in item.items() if k in ("type", "text", "x", "y", "font_name", "font_size", "fill", "path", "width", "height")}
            data["items"].append(entry)

        file_path = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if file_path:
            with open(file_path, "w") as f:
                json.dump(data, f, indent=2)
            self.status_var.set(f"Exported JSON: {os.path.basename(file_path)}")

    def import_template_json(self):
        file_path = filedialog.askopenfilename(filetypes=[("JSON", "*.json"), ("All", "*.*")])
        if not file_path:
            return
        try:
            with open(file_path, "r") as f:
                data = json.load(f)
            for item in list(self.canvas.items):
                self.canvas.delete(item["id"])
            self.canvas.items = []
            for item in data.get("items", []):
                if item["type"] == "text":
                    self.canvas.add_text(
                        text=item.get("text", "Text"),
                        x=item.get("x", 10),
                        y=item.get("y", 10),
                        font_name=item.get("font_name", "Arial"),
                        font_size=item.get("font_size", 24),
                        fill=item.get("fill", "black"),
                    )
                elif item["type"] == "image":
                    self.canvas.add_image(
                        item.get("path", ""),
                        x=item.get("x", 10),
                        y=item.get("y", 10),
                        width=item.get("width", 100),
                        height=item.get("height", 100),
                    )
            self.status_var.set(f"Imported JSON: {os.path.basename(file_path)}")
        except Exception as e:
            messagebox.showerror("Import Error", str(e))


if __name__ == "__main__":
    import sys
    if "--selftest" in sys.argv:
        from src.printer import build_text_print_bytes
        payload = build_text_print_bytes("Hello", density=PrintDensity.MEDIUM, speed=PrintSpeed.NORMAL, label_mode=LabelMode.DIE_CUT)
        assert isinstance(payload, bytes) and len(payload) > 0
        print("selftest_ok")
        sys.exit(0)

    root = tb.Window(themename="flatly")
    app = PrintMasterApp(root)
    root.mainloop()