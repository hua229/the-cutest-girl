"""Refinement and rich-content UI checks for emulator-5556's disposable APK.

Run after ios_ui_fixture.py --seed and after installing the current APK:
    python tools/ios_ui_refinement_qa.py --checks entrypoints fields meals
    python tools/ios_ui_refinement_qa.py --checks keyboard responsive

Only com.thecutestgirl.app.verification.iosui is targeted. No record is saved or
deleted, no real cloud action is invoked, and fixture hashes are checked before
and after the run. Each selected group writes JSON and XML/PNG evidence. System
display settings and the app appearance are restored in finally. Imports have
no ADB side effects; --help is also offline.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
import time
import xml.etree.ElementTree as ET

from ios_ui_fixture import Device, PACKAGE, PARTNER, read_schema
from ios_ui_qa import COMPONENT, UiQa, bounds, center, png_luminance, require


SERIAL = "emulator-5556"
OUTPUT = Path(__file__).resolve().parents[1] / ".verification/ui-refinement/qa"
CHECKS = ("entrypoints", "fields", "keyboard", "meals", "rich", "responsive", "permissions", "navigation", "syncstate", "dates", "mealdate")
HOMES = (("穿搭", "outfit"), ("饮食", "meal"), ("点滴", "event"))
FORBIDDEN_VISUAL = {"我的", "还没有评论", "写点评论", "ta 的评论",
                    "今天穿的也很漂亮", "值得记住的每一件小事"}


class RefinementQa(UiQa):
    def __init__(self, adb, output):
        super().__init__(adb, SERIAL, output)
        self.device = Device(adb, SERIAL)
        self.fixture = {}
        self.fixture_hash = None
        self.fixture_day = None

    def fixture_rows(self, table):
        require(table in ("outfits", "meals", "events"), "Unexpected fixture table")
        columns = [field["columnName"] for field in read_schema()[table]["fields"]]
        # Hex preserves newlines, Unicode and separators without requiring SQLite JSON1.
        query = ",".join(f'hex("{name}")' for name in columns)
        text = self.device.sql(f'SELECT {query} FROM "{table}" WHERE id GLOB \'iosqa-*\' ORDER BY id;')
        return [dict(zip(columns, (bytes.fromhex(value).decode("utf-8")
                                  for value in line.split("|"))))
                for line in text.splitlines() if line.strip()]

    def fixture_digest(self):
        rows = {table: self.fixture_rows(table) for table in ("outfits", "meals", "events")}
        digest = hashlib.sha256(json.dumps(rows, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        return rows, digest

    def preflight(self):
        require(self.serial == SERIAL and PACKAGE == "com.thecutestgirl.app.verification.iosui",
                "Only the fixed disposable emulator/package is allowed")
        require(self.shell("getprop", "ro.kernel.qemu") == "1", "Target is not an emulator")
        self.device.assert_no_cloud()
        super().preflight()
        self.fixture, self.fixture_hash = self.fixture_digest()
        require({name: len(rows) for name, rows in self.fixture.items()} ==
                {"outfits": 8, "meals": 6, "events": 4}, "Seed the 18 synthetic records first")
        require(all(row["dirty"] == "0" for rows in self.fixture.values() for row in rows),
                "Fixture rows are not clean; do not test a configured/synchronizing app")
        self.fixture_day = max(dt.date.fromisoformat(row["date"]) for row in self.fixture["meals"])
        self.report.update(fixture_counts={name: len(rows) for name, rows in self.fixture.items()},
                           fixture_date=self.fixture_day.isoformat(),
                           fixture_digest_before=self.fixture_hash)
        self.flush_report()

    def normal(self, theme=None):
        self.shell("wm", "size", "reset")
        self.shell("settings", "put", "system", "font_scale", "1.0")
        time.sleep(0.5)
        if theme and self.preferences().get("theme_mode", "system") != theme:
            self.set_theme(theme)
        else:
            self.home()

    @staticmethod
    def texts(root):
        return [node.get("text", "") for node in root.iter("node") if node.get("text")]

    def assert_clean_copy(self, root=None):
        if root is None:
            root, _ = self.dump()
        bad = [text for text in self.texts(root) if text in FORBIDDEN_VISUAL or
               "比如：" in text or "比如:" in text or "（可选）" in text]
        require(not bad, "Decorative/example copy is still visible: " + repr(bad))

    def notify(self, route, day, record_id=""):
        require(route in ("outfit", "meal", "event"), "Unexpected notification route")
        require(not record_id or record_id.startswith("iosqa-"), "Notification must reference a fixture")
        arguments = ["am", "start", "-W", "-n", COMPONENT, "-f", "0x20000000",
                     "--es", "extra_route", route, "--es", "extra_date", str(day)]
        # adb shell flattens arguments; an empty string would disappear and make
        # am reject the --es option before the app ever receives the intent.
        if record_id:
            arguments.extend(("--es", "extra_record_id", record_id))
        self.shell(*arguments)
        time.sleep(0.65)
        label = {"outfit": "穿搭", "meal": "饮食", "event": "点滴"}[route]
        self.expect(present=("设置", "新增记录", label))
        require(self.selected(label, bottom=True), "Notification selected the wrong tab")

    def fixture_home(self, name):
        self.home(name)
        if name == "饮食":
            self.notify("meal", self.fixture_day)
        elif name == "穿搭":
            self.notify("outfit", self.fixture_day)

    def content_leaf(self, text, *, contains=False, scroll=False):
        """Find a visible text leaf, including inside a card taller than the viewport.

        Card hit testing uses the visible leaf's point after verifying its clickable
        ancestor, rather than demanding that the entire long card fit onscreen.
        """
        for attempt in range(9 if scroll else 1):
            root, _ = self.dump()
            view = self.interactive_viewport()
            for node in root.iter("node"):
                value = node.get("text", "")
                if (text in value if contains else value == text):
                    left, top, right, bottom = bounds(node)
                    x, y = center(node)
                    if left < right and top < bottom and view[0] <= x <= view[2] and view[1] <= y <= view[3]:
                        return root, node
            if scroll and attempt < 8:
                self.scroll_up()
        raise AssertionError("No visible fixture text: " + text[:70])

    def tap_content(self, text, *, contains=False, scroll=False):
        root, node = self.content_leaf(text, contains=contains, scroll=scroll)
        parents = self.parents(root)
        target = node
        while target.get("clickable") != "true" and target in parents:
            target = parents[target]
        require(target.get("clickable") == "true" and target.get("enabled", "true") == "true",
                "Fixture content has no enabled action")
        self.tap_node(node)

    def field(self, label, scroll=True):
        for attempt in range(8 if scroll else 1):
            root, _ = self.dump()
            viewport = self.interactive_viewport()
            for node in root.iter("node"):
                if node.get("class") != "android.widget.EditText" or self.field_name(node) != label:
                    continue
                left, top, right, bottom = bounds(node)
                x, y = center(node)
                if node.get("enabled", "true") == "true" and node.get("focusable") == "true" and \
                        node.get("clickable") == "true" and left < right and top < bottom and \
                        viewport[0] <= x <= viewport[2] and viewport[1] <= y <= viewport[3]:
                    return node
            if scroll and attempt < 7:
                self.scroll_up()
        raise AssertionError("No usable EditText with stable field name: " + label)

    def field_name(self, node):
        """Accept the editor's own name or its unique non-focusable child name.

        Compose 1.7 can export the same semantics modifier as a child while an
        editor is focused. Never guess from a sibling label or another field.
        The raw location is recorded so this does not claim a flat XML property.
        """
        direct = node.get("content-desc", "")
        if direct:
            return direct
        names = {child.get("content-desc") for child in node.iter("node")
                 if child is not node and child.get("content-desc") and
                 child.get("focusable", "false") == "false" and bounds(child) == bounds(node)}
        require(len(names) <= 1, "An editor exposes conflicting descendant field names")
        if names:
            name = next(iter(names))
            observation = {"label": name, "name_location": "non_focusable_descendant",
                           "raw_editor_content_desc": "", "editor_text_present": bool(node.get("text"))}
            observations = self.report.setdefault("semantics_observations", [])
            if observation not in observations:
                observations.append(observation)
            return name
        return ""

    def scan_fields(self, expected, *, forbid=()):
        seen = set()
        last = None
        for _ in range(9):
            root, _ = self.dump()
            self.assert_clean_copy(root)
            visual = self.texts(root)
            for node in root.iter("node"):
                if node.get("class") != "android.widget.EditText":
                    continue
                label = self.field_name(node)
                require(bool(label), "An editable field has no accessible name")
                require(label not in forbid, "A read-only field is exposed as editable: " + label)
                require(label in visual, "Visible field label and EditText name differ: " + label)
                seen.add(label)
            fingerprint = tuple((node.get("text"), node.get("content-desc"), node.get("bounds"))
                                for node in root.iter("node"))
            if fingerprint == last:
                break
            last = fingerprint
            self.scroll_up()
        require(set(expected).issubset(seen), "Missing editable fields: " + repr(set(expected) - seen))
        return sorted(seen)

    def abandon_sheet(self):
        self.hide_keyboard()
        self.request_sheet_discard()
        self.expect(present=("放弃这次编辑？", "继续编辑", "放弃"))
        self.tap("放弃")
        self.expect(present=("设置", "新增记录"), absent=("保存",))

    def open_form(self, kind):
        home = {"outfit": "穿搭", "meal": "饮食", "moment": "点滴", "anniversary": "点滴"}[kind]
        self.home(home)
        if kind == "meal":
            # The seeded day has all six meals. A future day exercises the empty Sheet.
            self.notify("meal", self.fixture_day + dt.timedelta(days=1))
        self.tap("新增记录")
        if kind == "meal":
            self.expect(present=("记录饮食", "早餐", "午餐", "晚餐"))
            self.tap("早餐")
        elif kind == "anniversary":
            self.expect(present=("日常点滴", "纪念日"))
            self.tap("纪念日")
        titles = {"outfit": "记穿搭", "meal": "记录早餐", "moment": "记一条点滴", "anniversary": "记一个纪念日"}
        self.expect(present=(titles[kind], "保存"))

    def test_entrypoint(self, name):
        self.normal("light")
        self.fixture_home(name)
        for label in ("设置", "新增记录"):
            node = self.control(label)
            require(node.get("clickable") == "true" and node.get("enabled") == "true",
                    "Header action is not accessible: " + label)
        self.assert_clean_copy()
        self.open_settings()
        self.expect(present=("我的信息", "外观", "返回"))
        self.back()
        require(self.selected(name, bottom=True), "Returning from settings lost the original tab")
        self.tap("新增记录")
        self.expect(present=(("记录饮食", "早餐", "午餐", "晚餐") if name == "饮食" else
                             (("记穿搭", "保存") if name == "穿搭" else ("记一条点滴", "保存"))))
        self.assert_clean_copy()
        return {"home": name, "stable_actions": ["设置", "新增记录"]}

    def test_fields(self, kind):
        self.normal("light")
        self.open_form(kind)
        labels = {"outfit": ("备注",), "meal": ("内容",),
                  "moment": ("标题", "内容"), "anniversary": ("名称", "内容")}[kind]
        seen = self.scan_fields(labels)
        # Reopen at the top rather than tapping a label left above the viewport.
        self.open_form(kind)
        label = labels[0]
        self.tap_node(self.field(label))
        marker = "REFINEMENT_" + kind.upper()
        self.shell("input", "text", marker)
        node = self.field(label)
        require(self.field_name(node) == label and marker in node.get("text", "") and
                node.get("focused") == "true" and node.get("enabled") == "true",
                "Typing replaced the field's accessible name or lost its value")
        self.hide_keyboard()
        self.snapshot("filled-field-" + kind)
        self.abandon_sheet()
        return {"visible_labels_and_editable_names": seen, "typed_field": label,
                "accessible_name_preserved_after_input": True, "record_saved": False}

    def test_keyboard(self):
        self.normal("dark")
        detail = self.test_narrow_keyboard()
        require(self.preferences().get("theme_mode") == "dark", "Keyboard test did not stay in dark mode")
        return dict(detail, theme="dark")

    def test_meal_choice(self, label):
        self.normal("light")
        self.fixture_home("饮食")
        self.tap("新增记录")
        root = self.expect(present=("记录饮食", "早餐", "午餐", "晚餐"))
        visual = self.texts(root)
        require("记录" not in visual and "编辑" not in visual, "Meal rows retain redundant right-hand labels")
        self.assert_clean_copy(root)
        self.snapshot("meal-chooser-" + str(("早餐", "午餐", "晚餐").index(label)))
        self.tap(label)
        self.expect(present=("记录" + label, "保存", "返回"))
        self.scan_fields(("内容",), forbid=("评论",))
        self.back()
        return {"meal": label, "existing_own_meal_opened": True, "record_saved": False}

    def test_rich(self, theme):
        self.normal(theme)
        result = {}
        for name, code in HOMES:
            self.fixture_home(name)
            root, _ = self.dump()
            self.assert_clean_copy(root)
            require(any("合成" in text for text in self.texts(root)), "Seeded content is absent on " + name)
            path = self.snapshot("rich-" + theme + "-" + code)
            luma = png_luminance(path)
            require(luma < 110 if theme == "dark" else luma > 170,
                    "The page surface does not match the selected theme")
            result[code] = {"gutter_luminance": luma}
        self.fixture_home("饮食")
        partner = next(row for row in self.fixture["meals"] if row["origin"] == PARTNER)
        self.content_leaf(partner["originName"], scroll=True)
        self.snapshot("rich-" + theme + "-long-author-and-comment")
        self.content_leaf(partner["reply"][:24], contains=True, scroll=True)
        self.snapshot("rich-" + theme + "-long-comment-content")
        result["long_author_visible"] = True
        result["comment_content_visible"] = True
        return result

    def test_missing_photo(self):
        self.normal("light")
        self.fixture_home("穿搭")
        self.content_leaf("照片未同步", scroll=True)
        # Do not tap retry: this check is display-only and must never request sync.
        return {"missing_photo_placeholder_visible": True, "retry_invoked": False}

    def replace_fixture_preferences(self, raw):
        """Transport only the disposable package's non-secret fixture settings."""
        self.device.assert_no_cloud()
        local = self.output / "fixture-settings.xml"
        local.write_bytes(raw)
        remote = "/data/local/tmp/tcg-iosui-fixture-settings.xml"
        self.adb("push", str(local), remote)
        self.shell("run-as", PACKAGE, "cp", remote, "shared_prefs/settings.xml")
        digest = self.shell("run-as", PACKAGE, "sha256sum", "shared_prefs/settings.xml").split()[0]
        require(digest == hashlib.sha256(raw).hexdigest(),
                "Fixture preference transfer did not preserve bytes")

    def test_sync_failure_state(self):
        self.normal("light")
        self.shell("am", "force-stop", PACKAGE)
        # adb shell can normalize line endings; exec-out preserves the file bytes.
        original = self.adb("exec-out", "run-as", PACKAGE, "cat", "shared_prefs/settings.xml", binary=True)
        root = ET.fromstring(original)
        for child in list(root):
            if child.get("name") in {"last_sync_at", "last_sync_result"}:
                root.remove(child)
        ET.SubElement(root, "long", name="last_sync_at", value=str(int(time.time() * 1000)))
        ET.SubElement(root, "string", name="last_sync_result").text = "同步失败：合成离线状态"
        try:
            self.replace_fixture_preferences(ET.tostring(root, encoding="utf-8", xml_declaration=True))
            for theme in ("light", "dark"):
                self.normal(theme)
                self.fixture_home("穿搭")
                tree, label = self.content_leaf("照片同步失败", scroll=True)
                self.expect(present=("重试",), absent=("点一下重新同步", "同步完成后会自动出现"))
                parents = self.parents(tree)
                action = label
                while action.get("clickable") != "true" and action in parents:
                    action = parents[action]
                require(action.get("clickable") == "true" and action.get("enabled") == "true",
                        "The failed photo has no enabled retry surface")
                self.snapshot("sync-failure-photo-" + theme)
                self.home()
                self.open_settings()
                self.expect(present=("同步失败",))
                self.tap("云同步")
                self.expect(present=("同步失败", "合成离线状态"))
                self.snapshot("sync-failure-settings-" + theme)
            return {"themes": ["light", "dark"], "status_source": "isolated synthetic persisted state",
                    "cloud_request_invoked": False, "retry_invoked": False}
        finally:
            self.shell("am", "force-stop", PACKAGE)
            self.replace_fixture_preferences(original)
            self.report["synthetic_sync_preferences_restored"] = True
            self.restart()

    def test_five_digits(self):
        self.normal("dark")
        self.narrow_large_font()
        self.home("点滴")
        today = dt.date.fromisoformat(self.shell("date", "+%F"))
        expected = str((today - dt.date(1900, 1, 1)).days + 1)
        require(len(expected) == 5, "The 1900 fixture does not currently represent five digits")
        root, numeral = self.content_leaf(expected)
        parents = self.parents(root)
        card = numeral
        while card in parents and card.get("clickable") != "true":
            card = parents[card]
        require(card.get("clickable") == "true", "Anniversary has no interactive card")
        number_bounds, card_bounds = bounds(numeral), bounds(card)
        width, height = self.size()
        require(0 <= number_bounds[0] < number_bounds[2] <= width and
                0 <= number_bounds[1] < number_bounds[3] <= height,
                "Five-digit numeral extends outside the screen")
        require(card_bounds[0] <= number_bounds[0] and number_bounds[2] <= card_bounds[2],
                "Five-digit numeral extends outside its anniversary card")
        require("…" not in numeral.get("text", ""), "Anniversary numeral is ellipsized")
        return {"text": expected, "numeral_bounds": number_bounds, "card_bounds": card_bounds,
                "width_dp": 320, "font_scale": 2.0, "theme": "dark",
                "visual_review": "PNG evidence should also be reviewed for complete glyph rendering."}

    def test_permissions(self, partner=False):
        self.normal("light")
        self.fixture_home("饮食")
        if partner:
            row = next(row for row in self.fixture["meals"] if row["origin"] == PARTNER and row["mealType"] == "0")
            self.tap_record_author(row["originName"])
        else:
            self.tap("新增记录")
            self.tap("早餐")
        self.expect(present=("记录早餐", "保存", "返回"))
        seen, all_text = set(), set()
        last = None
        for _ in range(9):
            root, _ = self.dump()
            self.assert_clean_copy(root)
            all_text.update(self.texts(root))
            seen.update(self.field_name(node) for node in root.iter("node")
                        if node.get("class") == "android.widget.EditText")
            signature = tuple((node.get("text"), node.get("bounds")) for node in root.iter("node"))
            if signature == last:
                break
            last = signature
            self.scroll_up()
        expected = {"评论"} if partner else {"内容"}
        require(seen == expected, "Wrong ownership/edit permissions: " + repr(seen))
        deletion = any("删除这条" in text for text in all_text)
        require(not deletion if partner else deletion, "Delete action does not match record ownership")
        require(any("合成" in text for text in all_text), "Record author/content is no longer readable")
        self.snapshot("partner-comment-only" if partner else "own-content-editor")
        self.back()
        return {"view": "partner" if partner else "own", "editable_fields": sorted(seen),
                "delete_action_present": deletion, "saved_or_deleted": False}

    def tap_record_author(self, author):
        # The same person's name can also appear as the reply author on MY card.
        # Select a card whose first presented text is its uploader, not an
        # arbitrary occurrence of the name in a comment farther down the card.
        for attempt in range(9):
            root, _ = self.dump()
            parents = self.parents(root)
            viewport = self.interactive_viewport()
            for leaf in root.iter("node"):
                if leaf.get("text") != author:
                    continue
                card = leaf
                while card.get("clickable") != "true" and card in parents:
                    card = parents[card]
                text = self.texts(card)
                x, y = center(leaf)
                if card.get("clickable") == "true" and text and text[0] == author and \
                        viewport[0] <= x <= viewport[2] and viewport[1] <= y <= viewport[3]:
                    self.tap_node(leaf)
                    return
            if attempt < 8:
                self.scroll_up()
        raise AssertionError("Could not distinguish the partner uploader from a comment author")

    def test_top_return(self):
        self.normal("light")
        self.fixture_home("穿搭")
        month = f"{self.fixture_day.year}年{self.fixture_day.month}月"
        self.expect(present=(month,))
        self.scroll_up()
        self.scroll_up()
        self.expect(present=("设置", "新增记录"), absent=(month,))
        self.snapshot("outfit-collapsed-header")
        self.tap("穿搭", bottom=False)
        self.expect(present=(month,))
        return {"collapsed_actions_preserved": True, "title_returned_to_top": True}

    def test_notification(self):
        self.normal("light")
        self.home("饮食")
        previous_month = self.fixture_day.replace(day=1) - dt.timedelta(days=1)
        self.notify("outfit", previous_month, "iosqa-notification-empty-date")
        self.expect(present=(f"{previous_month.year}年{previous_month.month}月",))
        self.snapshot("notification-cross-month-outfit")
        event = next(row for row in self.fixture["events"] if row["id"] == "iosqa-moment-text")
        self.notify("event", event["date"], event["id"])
        self.content_leaf(event["title"])
        self.snapshot("notification-target-moment")
        return {"outfit_month": previous_month.strftime("%Y-%m"), "event_target": event["id"],
                "delivery": "Activity intent extras; no network notification requested"}

    def test_outfit_dates(self):
        self.normal("light")
        self.fixture_home("穿搭")
        first = self.fixture_day.replace(day=1)
        previous = first - dt.timedelta(days=1)
        label = lambda day: f"{day.year}年{day.month}月"
        self.tap("上个月")
        self.expect(present=(label(previous),))
        self.tap("下个月")
        self.expect(present=(label(first),))
        tree, _ = self.dump()
        day_labels = list(dict.fromkeys(node.get("content-desc", "") for node in tree.iter("node")
                                      if re.fullmatch(rf"{first.year}年{first.month}月\d+日", node.get("content-desc", ""))))
        day_label = None
        for value in day_labels:
            if self.selected(value):
                continue
            try:
                self.control(value)
            except AssertionError:
                continue
            day_label = value
            break
        require(day_label, "Date strip has no visible alternative day")
        self.tap(day_label)
        require(self.selected(day_label), "Date strip did not select the requested full date")
        self.snapshot("outfit-date-strip-selected")
        self.tap(label(first))
        self.expect(present=("选择月份", "确定", "取消"))
        if previous.year < first.year:
            self.tap("上一年")
        self.tap(f"{previous.month}月")
        self.tap("确定")
        self.expect(present=(label(previous),))
        self.snapshot("outfit-month-picker-applied")
        return {"previous_next_month": True, "date_strip_selected": day_label,
                "month_picker_applied": previous.strftime("%Y-%m")}

    def test_meal_dates(self):
        self.normal("light")
        self.fixture_home("饮食")
        def label(day):
            return f"{day.month}月{day.day}日 星期" + "一二三四五六日"[day.weekday()]
        following = self.fixture_day + dt.timedelta(days=1)
        self.tap("后一天")
        self.expect(present=(label(following), "记早餐", "记午餐", "记晚餐"))
        self.tap("前一天")
        self.expect(present=(label(self.fixture_day),))
        # The date label shares a parent with the separate "回到今天" action.
        # Click its verified text point, not the center of that entire parent.
        self.tap_content(label(self.fixture_day))
        self.expect(present=("确定", "取消"))
        self.snapshot("meal-date-picker")
        self.tap("取消")
        self.expect(present=(label(self.fixture_day),))
        return {"previous_next_day": True, "returned_date": self.fixture_day.isoformat(),
                "date_picker_cancel_retained_date": True}

    def run_checks(self, groups):
        try:
            self.preflight()
            self.baseline()
            if "entrypoints" in groups:
                for name, code in HOMES:
                    self.check("entrypoints-" + code, lambda name=name: self.test_entrypoint(name))
            if "fields" in groups:
                for kind in ("outfit", "meal", "moment", "anniversary"):
                    self.check("field-labels-" + kind, lambda kind=kind: self.test_fields(kind))
            if "keyboard" in groups:
                self.check("dark-narrow-keyboard-continue-discard", self.test_keyboard)
            if "meals" in groups:
                for label, code in (("早餐", "breakfast"), ("午餐", "lunch"), ("晚餐", "dinner")):
                    self.check("meal-chooser-" + code, lambda label=label: self.test_meal_choice(label))
            if "rich" in groups:
                for theme in ("light", "dark"):
                    self.check("rich-content-" + theme, lambda theme=theme: self.test_rich(theme))
                self.check("missing-photo-placeholder", self.test_missing_photo)
            if "responsive" in groups:
                self.check("dark-320dp-200-five-digit-anniversary", self.test_five_digits)
            if "permissions" in groups:
                self.check("own-record-edit-and-delete-availability", self.test_permissions)
                self.check("partner-comment-only-no-delete", lambda: self.test_permissions(True))
            if "navigation" in groups:
                self.check("outfit-collapse-and-return-to-top", self.test_top_return)
                self.check("notification-date-and-record-targets", self.test_notification)
            if "syncstate" in groups:
                self.check("synthetic-sync-failure-light-and-dark", self.test_sync_failure_state)
            if "dates" in groups:
                self.check("outfit-month-picker-and-date-strip", self.test_outfit_dates)
            if "dates" in groups or "mealdate" in groups:
                self.check("meal-previous-next-and-date-picker", self.test_meal_dates)
        except KeyboardInterrupt:
            self.report["fatal_error"] = "Interrupted; restoring display settings and app appearance"
        except Exception as error:
            self.report["fatal_error"] = type(error).__name__ + ": " + str(error)
            try:
                self.snapshot("fatal-error")
            except Exception as capture_error:
                self.report["fatal_capture_error"] = str(capture_error)
        finally:
            self.restore()
            if self.fixture_hash:
                try:
                    _, after = self.fixture_digest()
                    self.report["fixture_digest_after"] = after
                    require(after == self.fixture_hash, "Fixture records changed despite the no-save/no-delete contract")
                    self.report["fixture_unchanged"] = True
                except Exception as error:
                    self.report["fixture_unchanged"] = False
                    self.report["fixture_integrity_error"] = str(error)
            self.report["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
            self.report["passed"] = bool(self.report["checks"]) and not self.report.get("fatal_error") and all(
                item["status"] == "passed" for item in self.report["checks"]
            ) and all(item["status"] == "restored" for item in self.report["restoration"]) and \
                self.report.get("fixture_unchanged", False)
            self.flush_report()
        print(json.dumps({"passed": self.report["passed"], "report": str(self.report_path),
                          "checks": len(self.report["checks"]), "screens": len(self.snapshots)}, ensure_ascii=True))
        return 0 if self.report["passed"] else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=shutil.which("adb") or "E:/appdate/Android/Sdk/platform-tools/adb.exe")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--checks", nargs="+", choices=CHECKS, default=list(CHECKS))
    args = parser.parse_args(argv)
    return RefinementQa(args.adb, args.output).run_checks(args.checks)


if __name__ == "__main__":
    sys.exit(main())
