"""UI smoke checks for the isolated verification APK; Python standard library only.

Example:
    python tools/ios_ui_qa.py --adb E:/appdate/Android/Sdk/platform-tools/adb.exe

The package is deliberately not configurable. No app data is cleared, no records
are saved, and no sync/export/restore/battery permission action is invoked. A
configured cloud account in the verification package makes preflight fail before
launch. System display settings are restored in finally, including on failures.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zlib


PACKAGE = "com.thecutestgirl.app.verification.iosui"
PRODUCTION_PACKAGE = "com.thecutestgirl.app"
COMPONENT = PACKAGE + "/com.thecutestgirl.app.MainActivity"
WORKSPACE = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = WORKSPACE / ".verification" / "ios-ui"
GROUPS = ("homes", "settings", "themes", "navigation", "responsive")
MODULES = ("我的信息", "外观", "云同步", "数据备份", "后台不被冻结")
MODE_LABELS = {"system": "跟随系统", "light": "浅色", "dark": "深色"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def bounds(node):
    values = tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
    require(len(values) == 4, "Control has no usable bounds")
    return values


def center(node):
    left, top, right, bottom = bounds(node)
    return (left + right) // 2, (top + bottom) // 2


def png_luminance(path):
    """Read a few gutter pixels from an Android PNG, without Pillow.

    Supports the 8-bit non-interlaced RGB/RGBA PNGs produced by screencap.
    Sampling the left page gutter avoids cards/photos and the system bars.
    """
    raw = Path(path).read_bytes()
    require(raw[:8] == b"\x89PNG\r\n\x1a\n", "Screenshot is not a PNG")
    offset, compressed, dimensions = 8, [], None
    while offset + 12 <= len(raw):
        length = struct.unpack_from(">I", raw, offset)[0]
        kind = raw[offset + 4:offset + 8]
        payload = raw[offset + 8:offset + 8 + length]
        if kind == b"IHDR":
            dimensions = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.append(payload)
        elif kind == b"IEND":
            break
        offset += length + 12
    require(dimensions is not None, "PNG has no IHDR")
    width, height, depth, color_type, _, _, interlace = dimensions
    require(depth == 8 and color_type in (2, 6) and interlace == 0,
            "Unsupported screenshot PNG format")
    channels = 4 if color_type == 6 else 3
    stride = width * channels
    pixels = zlib.decompress(b"".join(compressed))
    require(len(pixels) >= height * (stride + 1), "Truncated PNG pixels")
    target_rows = {height // 3, height // 2, height * 2 // 3}
    x = max(2, min(10, width // 100))
    previous = bytearray(stride)
    samples = []
    for row_index in range(max(target_rows) + 1):
        start = row_index * (stride + 1)
        filter_type = pixels[start]
        row = bytearray(pixels[start + 1:start + stride + 1])
        require(filter_type in range(5), "Unsupported PNG row filter")
        if filter_type:
            for index in range(stride):
                left = row[index - channels] if index >= channels else 0
                above = previous[index]
                upper_left = previous[index - channels] if index >= channels else 0
                if filter_type == 1:
                    predictor = left
                elif filter_type == 2:
                    predictor = above
                elif filter_type == 3:
                    predictor = (left + above) // 2
                else:
                    value = left + above - upper_left
                    distances = (abs(value - left), abs(value - above), abs(value - upper_left))
                    predictor = (left, above, upper_left)[distances.index(min(distances))]
                row[index] = (row[index] + predictor) & 255
        if row_index in target_rows:
            red, green, blue = row[x * channels:x * channels + 3]
            samples.append(0.2126 * red + 0.7152 * green + 0.0722 * blue)
        previous = row
    return round(sum(samples) / len(samples), 2)


class UiQa:
    def __init__(self, adb_path, serial, output):
        require(PACKAGE == "com.thecutestgirl.app.verification.iosui" and PACKAGE != PRODUCTION_PACKAGE,
                "Refusing to target the production package")
        require(re.fullmatch(r"[A-Za-z0-9_.:-]+", serial) is not None, "Invalid device serial")
        self.adb_path = adb_path
        self.serial = serial
        self.output = Path(output).resolve()
        self.screens = self.output / "screens"
        self.screens.mkdir(parents=True, exist_ok=True)
        self.report_path = self.output / "report.json"
        self.remote_xml = "/sdcard/tcg-ios-ui-qa.xml"
        self.original = {}
        self.snapshots = []
        self.report = {
            "package": PACKAGE, "serial": serial,
            "started_at": datetime.now(timezone.utc).isoformat(),
            "checks": [], "restoration": [],
        }

    def adb(self, *arguments, check=True, timeout=35, binary=False):
        result = subprocess.run(
            [self.adb_path, "-s", self.serial, *map(str, arguments)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout,
        )
        if check and result.returncode:
            # Do not include stdout: run-as preference reads can contain private values.
            error = result.stderr.decode("utf-8", errors="replace").strip()[:300]
            raise RuntimeError("ADB command failed: " + error)
        if not check:
            return result
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

    def shell(self, *arguments, **kwargs):
        return self.adb("shell", *arguments, **kwargs)

    def flush_report(self):
        self.report_path.write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")

    def preferences(self):
        result = self.adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/settings.xml", check=False)
        # exec-out exit propagation differs across platform-tools versions.
        if b"No such file" in result.stderr + result.stdout:
            return {}
        require(result.returncode == 0 and result.stdout.lstrip().startswith((b"<?xml", b"<map")),
                "Cannot safely inspect verification preferences; debug run-as is required")
        root = ET.fromstring(result.stdout)
        return {node.get("name"): node.text or node.get("value", "") for node in root}

    def preflight(self):
        require(self.adb("get-state") == "device", "Device is not ready")
        require(self.shell("pm", "path", PACKAGE).startswith("package:"),
                "The isolated verification APK is not installed")
        # Stop only the isolated APK before inspecting it; never stop/clear production.
        self.shell("am", "force-stop", PACKAGE)
        prefs = self.preferences()
        require(not prefs.get("webdav_user", "").strip(),
                "Verification package has a cloud account; refusing to launch or contact cloud")
        size_text = self.shell("wm", "size")
        density_text = self.shell("wm", "density")
        night_text = self.shell("cmd", "uimode", "night")
        night = re.search(r"Night mode:\s*(\w+)", night_text, flags=re.I)
        require(night is not None and night.group(1).lower() in ("yes", "no", "auto"),
                "Night mode cannot be restored safely; expected yes/no/auto")
        physical = re.search(r"Physical size:\s*(\d+x\d+)", size_text)
        override = re.search(r"Override size:\s*(\d+x\d+)", size_text)
        density = re.findall(r"(?:Physical|Override) density:\s*(\d+)", density_text)
        require(physical is not None and density, "Cannot determine display size/density")
        self.original = {
            "font_scale": self.shell("settings", "get", "system", "font_scale"),
            "size": override.group(1) if override else None,
            "physical_size": physical.group(1), "density": int(density[-1]),
            "night": night.group(1).lower(),
            "virtual_keyboard": self.shell("settings", "get", "secure", "show_ime_with_hard_keyboard"),
            "theme": prefs.get("theme_mode", "system"),
        }
        self.report["original_system"] = self.original.copy()
        self.flush_report()

    def baseline(self):
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        self.shell("wm", "size", "reset")
        self.shell("settings", "put", "system", "font_scale", "1.0")
        self.shell("cmd", "uimode", "night", "no")
        time.sleep(0.8)

    def dump(self):
        for _ in range(4):
            result = self.shell("uiautomator", "dump", self.remote_xml, check=False, timeout=25)
            if result.returncode == 0 and b"dumped to" in result.stdout.lower():
                raw = self.adb("exec-out", "cat", self.remote_xml, binary=True)
                try:
                    return ET.fromstring(raw), raw
                except ET.ParseError:
                    pass
            time.sleep(0.3)
        raise AssertionError("UI hierarchy could not be dumped")

    @staticmethod
    def has_label(root, label):
        return any(label in (node.get("text", ""), node.get("content-desc", ""))
                   for node in root.iter("node"))

    def expect(self, present=(), absent=(), timeout=12):
        deadline = time.monotonic() + timeout
        while True:
            root, _ = self.dump()
            missing = [label for label in present if not self.has_label(root, label)]
            unexpected = [label for label in absent if self.has_label(root, label)]
            if not missing and not unexpected:
                return root
            if time.monotonic() >= deadline:
                raise AssertionError("Missing controls: " + repr(missing) + "; unexpected: " + repr(unexpected))
            time.sleep(0.25)

    @staticmethod
    def parents(root):
        return {child: parent for parent in root.iter() for child in parent}

    def inset_sources(self):
        """Visible content insets, rather than the often full-screen IME window."""
        text = self.shell("dumpsys", "window", "displays")
        sources = []
        for line in text.splitlines():
            kind = re.search(r"\btype=(statusBars|navigationBars|ime)\b", line)
            if kind is None or re.search(r"\bvisible=true\b", line) is None:
                continue
            # visibleFrame is the IME's actual opaque content, if supplied.
            frame = re.search(r"\bvisibleFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", line)
            if frame is None:
                frame = re.search(r"\bframe=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", line)
            if frame:
                rectangle = tuple(map(int, frame.groups()))
                if rectangle[2] > rectangle[0] and rectangle[3] > rectangle[1]:
                    sources.append((kind.group(1), rectangle))
        return sources

    def interactive_viewport(self):
        width, height = self.size()
        sources = self.inset_sources()
        density = self.original.get("density", 160)
        top = 0 if any(kind == "statusBars" for kind, _ in sources) else round(24 * density / 160)
        bottom = height if any(kind == "navigationBars" for kind, _ in sources) else height - round(48 * density / 160)
        left, right = 0, width
        for _, (x1, y1, x2, y2) in sources:
            if x2 - x1 >= width // 2:
                if y1 <= 0 < y2 < height // 2:
                    top = max(top, y2)
                elif y2 >= height and y1 > 0:
                    bottom = min(bottom, y1)
            elif y2 - y1 >= height // 2:
                if x1 <= 0 < x2 < width // 2:
                    left = max(left, x2)
                elif x2 >= width and x1 > 0:
                    right = min(right, x1)
        return left + 4, top + 4, right - 4, bottom - 4

    def control(self, label, *, bottom=False, clickable=True, scroll=False):
        for attempt in range(5 if scroll else 1):
            root, _ = self.dump()
            parents = self.parents(root)
            view_left, view_top, view_right, view_bottom = self.interactive_viewport()
            matches = []
            for node in root.iter("node"):
                if label not in (node.get("text", ""), node.get("content-desc", "")):
                    continue
                target = node
                if clickable:
                    while target.get("clickable") != "true" and target in parents:
                        target = parents[target]
                    if target.get("clickable") != "true":
                        continue
                if target.get("enabled", "true") != "true":
                    continue
                left, top, right, end = bounds(target)
                # A semantic node can extend underneath the system navigation bar.
                # Scroll until its whole hit region is safe; never clamp a tap into it.
                if view_left <= left < right <= view_right and view_top <= top < end <= view_bottom:
                    matches.append(target)
            if matches:
                return sorted(matches, key=lambda item: center(item)[1], reverse=bottom)[0]
            if attempt < 4 and scroll:
                self.scroll_up()
        raise AssertionError("No enabled visible control: " + label)

    def tap_node(self, node):
        self.shell("input", "tap", *center(node))
        time.sleep(0.25)

    def tap(self, label, **kwargs):
        self.tap_node(self.control(label, **kwargs))

    def size(self):
        sizes = re.findall(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", self.shell("wm", "size"))
        require(sizes, "Display size is unavailable")
        return tuple(map(int, sizes[-1]))

    def scroll_up(self):
        width, height = self.size()
        self.shell("input", "swipe", width // 2, height * 3 // 4, width // 2, height // 3, 350)
        time.sleep(0.2)

    def back(self):
        self.shell("input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.3)

    def restart(self):
        self.shell("am", "force-stop", PACKAGE)
        # Reset the verification Activity stack, not its database or preferences.
        self.shell("am", "start", "-W", "-n", COMPONENT, "-f", "0x10008000", timeout=45)
        self.expect(present=("设置", "新增记录", "穿搭"))

    def selected(self, label, *, bottom=False):
        root, _ = self.dump()
        candidates = [node for node in root.iter("node")
                      if label in (node.get("text", ""), node.get("content-desc", ""))]
        parents = self.parents(root)
        for node in sorted(candidates, key=lambda item: center(item)[1], reverse=bottom):
            while True:
                if node.get("selected") == "true" or node.get("checked") == "true":
                    return True
                if node not in parents:
                    break
                node = parents[node]
        return False

    def tab(self, name):
        if not self.selected(name, bottom=True):
            self.tap(name, bottom=True)
        self.expect(present=("设置", "新增记录", name))
        require(self.selected(name, bottom=True), "Bottom tab is not selected: " + name)

    def home(self, name="穿搭"):
        self.restart()
        if name != "穿搭":
            self.tab(name)

    def open_settings(self):
        self.tap("设置")
        self.expect(present=("设置", "返回", "我的信息", "外观"), absent=("新增记录",))

    def appearance(self):
        self.home()
        self.open_settings()
        self.tap("外观", scroll=True)
        self.expect(present=("外观", "跟随系统", "浅色", "深色", "返回"))

    def set_theme(self, mode):
        self.appearance()
        # Compose removes the accessibility click action from an already checked
        # radio option. It still exposes checked=true, which must be verified.
        if not self.selected(MODE_LABELS[mode]):
            self.tap(MODE_LABELS[mode])
        require(self.selected(MODE_LABELS[mode]), "Theme selection semantics did not update")
        deadline = time.monotonic() + 6
        while self.preferences().get("theme_mode") != mode:
            require(time.monotonic() < deadline, "Theme preference did not persist")
            time.sleep(0.2)
        self.back()
        self.back()
        self.expect(present=("设置", "新增记录"))

    def snapshot(self, name):
        safe_name = re.sub(r"[^a-zA-Z0-9_.-]+", "-", name).strip("-")
        prefix = self.screens / (f"{len(self.snapshots) + 1:03d}-" + safe_name)
        _, raw = self.dump()
        prefix.with_suffix(".xml").write_bytes(raw)
        png = self.adb("exec-out", "screencap", "-p", binary=True)
        require(png.startswith(b"\x89PNG\r\n\x1a\n"), "Screencap returned no PNG")
        prefix.with_suffix(".png").write_bytes(png)
        entry = {"name": name, "png": str(prefix.with_suffix(".png")), "xml": str(prefix.with_suffix(".xml"))}
        self.snapshots.append(entry)
        return prefix.with_suffix(".png")

    def check(self, name, action):
        start, before = time.monotonic(), len(self.snapshots)
        entry = {"name": name, "status": "passed"}
        try:
            detail = action()
            if detail is not None:
                entry["details"] = detail
            self.snapshot(name)
        except Exception as error:
            entry.update(status="failed", error=type(error).__name__ + ": " + str(error))
            try:
                self.snapshot(name + "-failed")
            except Exception as capture_error:
                entry["capture_error"] = str(capture_error)
        entry["duration_seconds"] = round(time.monotonic() - start, 2)
        entry["screens"] = self.snapshots[before:]
        self.report["checks"].append(entry)
        self.flush_report()
        print(entry["status"].upper() + ": " + name, flush=True)

    def test_home(self, name):
        self.home(name)
        self.snapshot("home-" + {"穿搭": "outfit", "饮食": "meal", "点滴": "event"}[name])
        self.open_settings()
        self.snapshot("settings-from-" + {"穿搭": "outfit", "饮食": "meal", "点滴": "event"}[name])
        self.back()
        require(self.selected(name, bottom=True), "Settings return lost the original tab")
        self.tap("新增记录")
        if name == "穿搭":
            self.expect(present=("记穿搭", "归到", "保存"))
        elif name == "饮食":
            self.expect(present=("记录饮食", "早餐", "午餐", "晚餐"))
        else:
            self.expect(present=("记一条点滴", "日常点滴", "纪念日", "保存"))

    def test_meal_type(self, meal_name):
        self.home("饮食")
        self.tap("新增记录")
        self.expect(present=("记录饮食", "早餐", "午餐", "晚餐"))
        self.tap(meal_name)
        self.expect(present=("记录" + meal_name, "保存"))
        # Existing records legitimately open the full editor; blank ones open the Sheet.
        root, _ = self.dump()
        require(any(node.get("class") == "android.widget.EditText" for node in root.iter("node")),
                "Meal chooser did not open a form")

    def test_module(self, name):
        self.home()
        self.open_settings()
        self.tap(name, scroll=True)
        expected = {
            "我的信息": ("我的昵称",), "外观": ("跟随系统", "浅色", "深色"),
            "云同步": ("服务器地址", "账号", "应用密码", "测试并开启同步"),
            "数据备份": ("导出备份", "从备份恢复"), "后台不被冻结": (),
        }[name]
        self.expect(present=(name, "返回", *expected))
        self.snapshot("module-" + str(MODULES.index(name)))
        if name == "云同步":
            self.tap("同步帮助", scroll=True)
            self.expect(present=("同步帮助", "知道了"))
            self.snapshot("sync-help")
            self.tap("知道了")
        self.tap("返回")
        self.expect(present=("设置", "我的信息", "外观"))

    def test_theme(self, mode):
        self.set_theme(mode)
        image_path = self.snapshot("theme-" + mode)
        luma = png_luminance(image_path)
        require(luma > 170 if mode == "light" else luma < 110,
                f"Theme surface luminance does not match {mode}: {luma}")
        return {"gutter_luminance": luma, "stored_theme": self.preferences().get("theme_mode")}

    def test_persistence(self):
        self.set_theme("dark")
        self.restart()
        require(self.preferences().get("theme_mode") == "dark", "Dark mode was lost after cold launch")
        require(png_luminance(self.snapshot("dark-after-cold-launch")) < 110,
                "Cold launch did not restore a dark surface")
        self.open_settings()
        self.tap("外观")
        require(self.selected("深色"), "Persisted dark mode is not selected")

    def test_system_theme(self):
        self.set_theme("system")
        values = {}
        for night, dark in (("yes", True), ("no", False)):
            self.shell("cmd", "uimode", "night", night)
            time.sleep(0.6)
            self.expect(present=("设置", "新增记录"))
            luma = png_luminance(self.snapshot("system-night-" + night))
            require(luma < 110 if dark else luma > 170, "System theme did not follow night=" + night)
            require(self.preferences().get("theme_mode") == "system", "System following overwrote the preference")
            values[night] = luma
        return values

    def test_manual_override(self):
        values = {}
        for mode, night in (("light", "yes"), ("dark", "no")):
            self.set_theme(mode)
            self.shell("cmd", "uimode", "night", night)
            time.sleep(0.6)
            self.expect(present=("设置", "新增记录"))
            luma = png_luminance(self.snapshot(mode + "-system-night-" + night))
            require(luma > 170 if mode == "light" else luma < 110,
                    "Manual theme did not override the system")
            values[mode] = luma
        return values

    def test_bottom_taps(self):
        self.home()
        for name in ("饮食", "点滴", "穿搭"):
            self.tab(name)
            self.snapshot("bottom-tap-" + {"穿搭": "outfit", "饮食": "meal", "点滴": "event"}[name])

    def test_bottom_drag(self):
        self.home()
        root, _ = self.dump()
        parents = self.parents(root)
        width, height = self.size()
        tabs = []
        for name in ("穿搭", "饮食", "点滴"):
            labels = [node for node in root.iter("node") if node.get("text") == name]
            require(labels, "Bottom tab label is absent: " + name)
            node = max(labels, key=lambda item: center(item)[1])
            # Drag starts on the selected tab, whose a11y click action is deliberately
            # absent. Require the actual focusable selection node, not a text leaf.
            while node in parents and not (
                node.get("focusable") == "true" and
                (node.get("clickable") == "true" or node.get("selected") == "true")
            ):
                node = parents[node]
            require(node.get("enabled") == "true" and node.get("focusable") == "true" and
                    (node.get("clickable") == "true" or node.get("selected") == "true") and
                    bounds(node)[1] > height // 2,
                    "No verified bottom tab selection region: " + name)
            tabs.append(node)
        require(bounds(tabs[0])[0] > 0 and bounds(tabs[-1])[2] < width,
                "Bottom navigation no longer floats inside the screen margins")
        start, end = center(tabs[0]), center(tabs[-1])
        self.shell("input", "touchscreen", "motionevent", "DOWN", *start)
        try:
            time.sleep(0.85)
            for step in range(1, 13):
                point = tuple(round(left + (right - left) * step / 12) for left, right in zip(start, end))
                self.shell("input", "touchscreen", "motionevent", "MOVE", *point)
                time.sleep(0.04)
        finally:
            self.shell("input", "touchscreen", "motionevent", "UP", *end)
        self.expect(present=("设置", "新增记录", "日常点滴"))
        require(self.selected("点滴", bottom=True), "Long press and drag did not select the final tab")

    def narrow_large_font(self):
        density = self.original["density"]
        width, height = round(320 * density / 160), round(711 * density / 160)
        self.shell("wm", "size", f"{width}x{height}")
        self.shell("settings", "put", "system", "font_scale", "2.0")
        time.sleep(0.8)
        return {"width_dp": 320, "pixels": [width, height], "font_scale": 2.0}

    def test_narrow_homes(self):
        detail = self.narrow_large_font()
        for name in ("穿搭", "饮食", "点滴"):
            self.home(name)
            self.snapshot("narrow-" + {"穿搭": "outfit", "饮食": "meal", "点滴": "event"}[name])
            width, height = self.size()
            for label in ("设置", "新增记录"):
                left, top, right, bottom = bounds(self.control(label))
                require(0 <= left < right <= width and 0 <= top < bottom <= height,
                        "Header action is clipped at 320dp/200%: " + label)
            self.open_settings()
            self.snapshot("narrow-settings-from-" + {"穿搭": "outfit", "饮食": "meal", "点滴": "event"}[name])
        return detail

    def ime_visible(self):
        text = self.shell("dumpsys", "input_method")
        return bool(re.search(r"(?:mInputShown|inputShown|mIsInputViewShown)=true", text))

    def ime_top(self):
        tops = [rectangle[1] for kind, rectangle in self.inset_sources() if kind == "ime" and rectangle[1] > 0]
        if tops:
            return min(tops)
        windows = self.shell("dumpsys", "window", "windows")
        for block in re.split(r"\n\s*Window #", windows):
            if not re.search(r"^\d+ Window\{[^\n]*\bInputMethod\}", block) or "isVisible=true" not in block:
                continue
            frame = re.search(r"(?:mFrame|frame)=\[\d+,(-?\d+)\]\[\d+,(\d+)\]", block)
            if frame and int(frame.group(2)) > int(frame.group(1)) > 0:
                inset = re.search(r"mGiven(?:Visible|Content)Insets=\[\d+,(\d+)\]", block)
                require(inset is not None, "IME window lacks its content insets")
                return int(frame.group(1)) + int(inset.group(1))
        raise AssertionError("Keyboard is shown, but its top edge could not be determined")

    def hide_keyboard(self):
        if self.ime_visible():
            self.back()
            deadline = time.monotonic() + 5
            while self.ime_visible() and time.monotonic() < deadline:
                time.sleep(0.2)

    def request_sheet_discard(self):
        # Back may first dismiss IME. Stop immediately once the confirmation opens.
        for _ in range(3):
            root, _ = self.dump()
            if self.has_label(root, "放弃这次编辑？"):
                return
            self.back()
        self.expect(present=("放弃这次编辑？",))

    def test_narrow_keyboard(self):
        self.narrow_large_font()
        self.shell("settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
        self.home("点滴")
        self.tap("新增记录")
        self.expect(present=("记一条点滴", "保存"))
        root, _ = self.dump()
        fields = [node for node in root.iter("node") if node.get("class") == "android.widget.EditText"
                  and node.get("enabled", "true") == "true"]
        require(fields, "New event Sheet has no visible editable title")
        self.tap_node(fields[0])
        self.shell("input", "text", "TCG_QA_DRAFT")
        self.expect(present=("TCG_QA_DRAFT", "保存"))
        deadline = time.monotonic() + 6
        while not self.ime_visible() and time.monotonic() < deadline:
            time.sleep(0.2)
        require(self.ime_visible(), "The software keyboard did not open")
        keyboard_top = self.ime_top()
        save_bounds = bounds(self.control("保存"))
        require(0 <= save_bounds[1] < save_bounds[3] <= keyboard_top,
                "Save action is obscured by the keyboard at 320dp/200%")
        self.snapshot("narrow-keyboard-save-visible")
        self.request_sheet_discard()
        self.expect(present=("放弃这次编辑？", "继续编辑", "放弃"))
        self.snapshot("narrow-unsaved-confirm")
        self.tap("继续编辑")
        self.expect(present=("TCG_QA_DRAFT", "保存"), absent=("放弃这次编辑？",))
        self.snapshot("narrow-continue-editing-preserved")
        self.request_sheet_discard()
        self.expect(present=("放弃这次编辑？",))
        self.tap("放弃")
        self.expect(present=("设置", "新增记录"), absent=("TCG_QA_DRAFT", "保存"))
        return {"save_bounds": save_bounds, "keyboard_top": keyboard_top, "record_saved": False}

    def restore(self):
        if not self.original:
            return
        operations = [
            ("font_scale", lambda: self.restore_setting("system", "font_scale", self.original["font_scale"])),
            ("size", lambda: self.shell("wm", "size", self.original["size"] or "reset")),
            ("night", lambda: self.shell("cmd", "uimode", "night", self.original["night"])),
            ("virtual_keyboard", lambda: self.restore_setting("secure", "show_ime_with_hard_keyboard", self.original["virtual_keyboard"])),
            ("app_theme", lambda: self.set_theme(self.original["theme"] if self.original["theme"] in MODE_LABELS else "system")),
        ]
        for name, action in operations:
            item = {"setting": name, "status": "restored"}
            try:
                action()
            except Exception as error:
                item.update(status="failed", error=str(error))
            self.report["restoration"].append(item)
            self.flush_report()

    def restore_setting(self, namespace, key, value):
        if value in ("null", "", None):
            self.shell("settings", "delete", namespace, key)
        else:
            self.shell("settings", "put", namespace, key, value)

    def run(self, groups):
        try:
            self.preflight()
            self.baseline()
            if "homes" in groups:
                for name, code in (("穿搭", "outfit"), ("饮食", "meal"), ("点滴", "event")):
                    self.check("homes-" + code + "-settings-and-add", lambda name=name: self.test_home(name))
                for meal, code in (("早餐", "breakfast"), ("午餐", "lunch"), ("晚餐", "dinner")):
                    self.check("meal-chooser-" + code, lambda meal=meal: self.test_meal_type(meal))
            if "settings" in groups:
                for index, name in enumerate(MODULES):
                    self.check(f"settings-module-{index + 1}", lambda name=name: self.test_module(name))
            if "themes" in groups:
                self.check("theme-light", lambda: self.test_theme("light"))
                self.check("theme-dark", lambda: self.test_theme("dark"))
                self.check("theme-dark-cold-launch-persistence", self.test_persistence)
                self.check("theme-follows-system", self.test_system_theme)
                self.check("theme-manual-overrides-system", self.test_manual_override)
            if "navigation" in groups:
                self.check("bottom-tab-taps", self.test_bottom_taps)
                self.check("bottom-long-press-drag", self.test_bottom_drag)
            if "responsive" in groups:
                self.check("narrow-320dp-font-200-homes-settings", self.test_narrow_homes)
                self.check("narrow-keyboard-save-and-continue-editing", self.test_narrow_keyboard)
        except KeyboardInterrupt:
            self.report["fatal_error"] = "Interrupted; system settings restoration was requested"
        except Exception as error:
            self.report["fatal_error"] = type(error).__name__ + ": " + str(error)
        finally:
            self.restore()
            self.report["finished_at"] = datetime.now(timezone.utc).isoformat()
            self.report["passed"] = bool(self.report["checks"]) and not self.report.get("fatal_error") and all(
                item["status"] == "passed" for item in self.report["checks"]
            ) and all(item["status"] == "restored" for item in self.report["restoration"])
            self.flush_report()
        print(json.dumps({"passed": self.report["passed"], "report": str(self.report_path),
                          "checks": len(self.report["checks"]), "screens": len(self.snapshots)}, ensure_ascii=False), flush=True)
        return 0 if self.report["passed"] else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb") or "E:/appdate/Android/Sdk/platform-tools/adb.exe")
    parser.add_argument("--serial", default="emulator-5556")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--groups", nargs="+", choices=GROUPS, default=list(GROUPS))
    arguments = parser.parse_args(argv)
    return UiQa(arguments.adb, arguments.serial, arguments.output).run(arguments.groups)


if __name__ == "__main__":
    sys.exit(main())
