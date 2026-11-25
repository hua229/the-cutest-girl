"""Live theme-state checks and an optional screenshot gallery for the isolated APK.

Requires the current APK and ios_ui_fixture.py's 18 synthetic records:
    python tools/ios_ui_state_qa.py --checks state
    python tools/ios_ui_state_qa.py --checks gallery

Only emulator-5556/com.thecutestgirl.app.verification.iosui is supported through
RefinementQa's guards. No record is saved/deleted and no cloud operation runs.
The retention checks never restart/navigate home after their state is captured.
Gallery dialogs are cancelled, not confirmed. System settings, app appearance
and the fixture database digest are restored/checked in finally.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path
import re
import shutil
import sys
import time

from ios_ui_refinement_qa import RefinementQa
from ios_ui_qa import MODE_LABELS, MODULES, PACKAGE, bounds, center, png_luminance, require


OUTPUT = Path(__file__).resolve().parents[1] / ".verification/ui-refinement/qa-state"
CHECKS = ("state", "draft", "position", "gallery")
DRAFT_VALUES = {"标题": "STATE_QA_TITLE", "内容": "STATE_QA_BODY"}


class StateQa(RefinementQa):
    def preflight(self):
        super().preflight()
        # Bind new evidence to the installed APK when the device exposes sha256sum.
        paths = self.shell("pm", "path", PACKAGE).splitlines()
        if paths and paths[0].startswith("package:"):
            result = self.shell("sha256sum", paths[0][len("package:"):], check=False)
            digest = result.stdout.decode("utf-8", errors="replace").split()
            if result.returncode == 0 and digest and re.fullmatch(r"[0-9a-fA-F]{64}", digest[0]):
                self.report["installed_apk_sha256"] = digest[0].lower()
        self.flush_report()

    def swipe_scroll(self, toward_end, root=None):
        """Scroll the visible vertical form/list, not the fixed Save action or IME."""
        if root is None:
            root, _ = self.dump()
        viewport = self.interactive_viewport()
        candidates = []
        for node in root.iter("node"):
            if node.get("scrollable") != "true":
                continue
            left, top, right, bottom = bounds(node)
            left, top = max(left, viewport[0]), max(top, viewport[1])
            right, bottom = min(right, viewport[2]), min(bottom, viewport[3])
            if right > left and bottom - top >= 100:
                candidates.append((left, top, right, bottom))
        if not candidates:
            return False
        left, top, right, bottom = max(candidates, key=lambda item: (item[2] - item[0]) * (item[3] - item[1]))
        x = (left + right) // 2
        low, high = top + (bottom - top) * 3 // 4, top + (bottom - top) // 4
        start, end = (low, high) if toward_end else (high, low)
        self.shell("input", "swipe", x, start, x, end, 350)
        time.sleep(0.2)
        return True

    def form_start(self, target="标题"):
        """Reveal a named editor without pulling an already topped-out Sheet."""
        previous = None
        for _ in range(4):
            root, _ = self.dump()
            viewport = self.interactive_viewport()
            named = [node for node in root.iter("node")
                     if node.get("class") == "android.widget.EditText" and
                     self.field_name(node) == target and node.get("enabled", "true") == "true" and
                     node.get("focusable") == "true" and node.get("clickable") == "true"]
            for node in named:
                left, top, right, bottom = bounds(node)
                x, y = center(node)
                if left < right and top < bottom and viewport[0] <= x <= viewport[2] and viewport[1] <= y <= viewport[3]:
                    return True
            require(self.has_label(root, "保存"), "No active form while locating editor: " + target)
            # If the requested field is below us, pulling toward the start cannot
            # reveal it. Let field() scroll forward instead of dragging the Sheet.
            if named and all(center(node)[1] > viewport[3] for node in named):
                return False
            signature = tuple((node.get("text"), node.get("content-desc"), node.get("bounds"))
                              for node in root.iter("node"))
            if signature == previous:
                break
            previous = signature
            if not self.swipe_scroll(False, root):
                break
        return False

    def form_values(self, expected):
        self.form_start()
        # The Sheet title scrolls with its form; it need not remain on screen
        # once the first named editor is already safely interactive.
        self.expect(present=("保存",))
        values, previous = {}, None
        for _ in range(8):
            root, _ = self.dump()
            for node in root.iter("node"):
                if node.get("class") != "android.widget.EditText":
                    continue
                name = self.field_name(node)
                if name in expected:
                    values[name] = node.get("text", "")
            if set(expected).issubset(values):
                break
            signature = tuple((node.get("text"), node.get("bounds")) for node in root.iter("node"))
            if signature == previous or not self.swipe_scroll(True, root):
                break
            previous = signature
        require(values == expected, "Theme change lost/replaced the unsaved field values: " + repr(values))
        save = self.control("保存")
        require(save.get("enabled") == "true" and save.get("clickable") == "true",
                "The retained draft no longer has an enabled Save action")
        return values

    def wait_preference(self, mode):
        deadline = time.monotonic() + 6
        while self.preferences().get("theme_mode") != mode:
            require(time.monotonic() < deadline, "Theme preference did not update to " + mode)
            time.sleep(0.2)

    def live_night(self, mode):
        require(mode in ("yes", "no"), "Unexpected system night mode")
        self.shell("cmd", "uimode", "night", mode)
        time.sleep(0.8)
        require(self.preferences().get("theme_mode") == "system",
                "Live system-theme test stopped following the system")

    def test_system_draft(self):
        # Setup may restart. No home()/normal()/set_theme() is used after typing.
        self.normal("system")
        self.live_night("no")
        self.tab("点滴")
        self.tap("新增记录")
        self.expect(present=("记一条点滴", "保存", "标题"))
        for label, value in DRAFT_VALUES.items():
            self.form_start(label)
            self.tap_node(self.field(label))
            self.shell("input", "text", value)
            self.hide_keyboard()
        self.form_values(DRAFT_VALUES)
        observed = []
        for mode in ("yes", "no"):
            self.live_night(mode)
            values = self.form_values(DRAFT_VALUES)
            image_path = self.snapshot("system-draft-night-" + mode)
            luminance = png_luminance(image_path)
            require(luminance < 110 if mode == "yes" else luminance > 170,
                    "The open Sheet did not render the new system theme")
            observed.append({"night": mode, "fields": values, "save_enabled": True,
                             "sheet_gutter_luminance": luminance})
        self.request_sheet_discard()
        self.expect(present=("放弃这次编辑？", "继续编辑"))
        self.tap("继续编辑")
        self.form_values(DRAFT_VALUES)
        self.snapshot("system-draft-continue-retains-values")
        self.abandon_sheet()
        require(self.selected("点滴", bottom=True), "Discarding the retained Sheet changed the selected tab")
        return {"transitions": observed, "continued_values_preserved": True,
                "record_saved": False, "qa_forced_restart_during_retention": False}

    def content_viewport(self, root, phase):
        """Exclude floating page chrome as well as the OS bars/keyboard."""
        system_view = self.interactive_viewport()
        parents = self.parents(root)
        _, height = self.size()
        header, footer = {}, {}
        for label in ("设置", "新增记录"):
            matches = []
            for leaf in root.iter("node"):
                if label not in (leaf.get("text", ""), leaf.get("content-desc", "")):
                    continue
                action = leaf
                while action.get("clickable") != "true" and action in parents:
                    action = parents[action]
                if action.get("clickable") == "true" and action.get("enabled", "true") == "true":
                    rectangle = bounds(action)
                    if rectangle[1] < rectangle[3] < height // 2:
                        matches.append(rectangle)
            require(matches, "Cannot identify the floating header action region: " + label)
            header[label] = min(matches, key=lambda rectangle: rectangle[1])
        for label in ("穿搭", "饮食", "点滴"):
            matches = []
            for leaf in root.iter("node"):
                if label not in (leaf.get("text", ""), leaf.get("content-desc", "")):
                    continue
                action = leaf
                while action in parents and not (
                    action.get("focusable") == "true" and
                    (action.get("clickable") == "true" or action.get("selected") == "true")
                ):
                    action = parents[action]
                rectangle = bounds(action)
                if action.get("enabled", "true") == "true" and action.get("focusable") == "true" and \
                        (action.get("clickable") == "true" or action.get("selected") == "true") and \
                        height // 2 < rectangle[1] < rectangle[3]:
                    matches.append(rectangle)
            require(matches, "Cannot identify the floating bottom tab region: " + label)
            footer[label] = max(matches, key=lambda rectangle: rectangle[1])
        gap = max(1, round(12 * self.original["density"] / 160))
        viewport = (system_view[0], max(system_view[1], max(item[3] for item in header.values()) + gap),
                    system_view[2], min(system_view[3], min(item[1] for item in footer.values()) - gap))
        require(viewport[0] < viewport[2] and viewport[1] < viewport[3], "Page chrome leaves no usable content region")
        observation = {"phase": phase, "bounds": viewport, "system_interactive_bounds": system_view,
                       "header_action_bounds": header, "bottom_tab_bounds": footer, "safety_gap_px": gap}
        self.report.setdefault("content_viewport_observations", []).append(observation)
        self.flush_report()
        return viewport

    @staticmethod
    def fully_inside(rectangle, viewport):
        left, top, right, bottom = rectangle
        return viewport[0] <= left < right <= viewport[2] and viewport[1] <= top < bottom <= viewport[3]

    def capture_content_anchor(self):
        root, _ = self.dump()
        viewport, parents = self.content_viewport(root, "anchor-before"), self.parents(root)
        source_values = {row[key] for rows in self.fixture.values() for row in rows
                         for key in ("note", "title", "description", "reply", "originName", "replyName")
                         if row.get(key)}
        candidates = []
        for node in root.iter("node"):
            text = node.get("text", "")
            if text not in source_values:
                continue
            rectangle = bounds(node)
            if not self.fully_inside(rectangle, viewport):
                continue
            card = node
            while card.get("clickable") != "true" and card in parents:
                card = parents[card]
            if card.get("clickable") == "true":
                candidates.append({"text": text, "bounds": rectangle,
                                   "card_texts": self.texts(card), "content_viewport": viewport})
        require(candidates, "No visible synthetic content anchor after scrolling")
        return min(candidates, key=lambda item: (item["bounds"][1], item["bounds"][0]))

    def assert_content_anchor(self, expected, tab="饮食", diagnostic_key=None):
        self.expect(present=("设置", "新增记录", tab))
        require(self.selected(tab, bottom=True), "Theme change lost the selected tab")
        root, _ = self.dump()
        viewport = self.content_viewport(root, "anchor-after")
        parents, matches, candidates = self.parents(root), [], []
        tolerance = max(2, round(self.original["density"] / 160 * 2))
        for node in root.iter("node"):
            if node.get("text") != expected["text"]:
                continue
            card = node
            while card.get("clickable") != "true" and card in parents:
                card = parents[card]
            actual = bounds(node)
            card_texts = self.texts(card)
            deltas = [after - before for before, after in zip(expected["bounds"], actual)]
            card_matches = card.get("clickable") == "true" and card_texts == expected["card_texts"]
            position_matches = all(abs(delta) <= tolerance for delta in deltas)
            visible = self.fully_inside(actual, viewport)
            candidates.append({"bounds": actual, "card_texts": card_texts,
                               "card_clickable": card.get("clickable") == "true",
                               "card_texts_match": card_matches, "bounds_delta_px": deltas,
                               "within_position_tolerance": position_matches,
                               "fully_visible_in_content": visible})
            if card_matches:
                if position_matches and visible:
                    matches.append(actual)
        failure = None if matches else (
            "anchor_text_absent" if not candidates else
            "card_text_context_mismatch" if not any(item["card_texts_match"] for item in candidates) else
            "anchor_outside_content_viewport" if not any(item["card_texts_match"] and item["fully_visible_in_content"]
                                                        for item in candidates) else
            "scroll_position_changed")
        if diagnostic_key:
            observation = {"expected": expected, "tolerance_px": tolerance,
                           "content_viewport": viewport,
                           "candidates_with_same_text": candidates, "failure_reason": failure}
            self.report.setdefault(diagnostic_key, []).append(observation)
            self.flush_report()  # Preserve the before/candidate evidence even when assertion fails.
        require(matches, "Content anchor retention failed: " + str(failure))
        return {"tab": tab, "anchor_bounds": expected["bounds"], "tolerance_px": tolerance,
                "content_viewport": viewport}

    def prepare_scrolled_meal(self, theme):
        self.normal(theme)
        self.fixture_home("饮食")
        self.scroll_up()
        self.scroll_up()
        root, _ = self.dump()
        require(not any("回到今天" == text or "今天" == text for text in self.texts(root)),
                "Meal header did not collapse; a scrolled state was not established")
        return self.capture_content_anchor()

    def test_system_home_position(self):
        self.normal("system")
        self.live_night("no")
        # All restart/navigation setup happens before the anchor is captured.
        anchor = self.prepare_scrolled_meal("system")
        transitions = []
        for mode in ("yes", "no"):
            self.live_night(mode)
            transitions.append(dict(self.assert_content_anchor(anchor), night=mode))
            self.snapshot("system-home-scroll-night-" + mode)
        return {"transitions": transitions, "qa_forced_restart_during_retention": False}

    def test_manual_home_position(self):
        anchor = self.prepare_scrolled_meal("light")
        self.report["manual_theme_anchor_before"] = anchor
        self.flush_report()
        self.snapshot("manual-home-scroll-before")
        transitions = []
        for mode in ("dark", "light"):
            # Navigate through the existing stack. set_theme() would restart it.
            self.open_settings()
            self.tap("外观", scroll=True)
            self.expect(present=("跟随系统", "浅色", "深色", "返回"))
            if not self.selected(MODE_LABELS[mode]):
                self.tap(MODE_LABELS[mode])
            self.wait_preference(mode)
            self.back()
            self.back()
            self.report["manual_theme_target"] = mode
            detail = self.assert_content_anchor(anchor, diagnostic_key="manual_theme_anchor_observations")
            luminance = png_luminance(self.snapshot("manual-home-scroll-" + mode))
            require(luminance < 110 if mode == "dark" else luminance > 170,
                    "Manual theme did not render after returning to the same page")
            transitions.append(dict(detail, theme=mode, gutter_luminance=luminance))
        return {"transitions": transitions, "qa_forced_restart_during_retention": False}

    def open_gallery_form(self, kind, theme):
        if kind == "meal":
            self.home("饮食")
            self.notify("meal", self.fixture_day + dt.timedelta(days=1))
            self.tap("新增记录")
            self.expect(present=("记录饮食", "早餐", "午餐", "晚餐"))
            self.snapshot(theme + "-meal-chooser")
            self.tap("早餐")
            self.expect(present=("记录早餐", "保存", "内容"))
        else:
            self.open_form(kind)

    def open_meal_editor(self):
        self.fixture_home("饮食")
        self.tap("新增记录")
        self.tap("早餐")
        self.expect(present=("记录早餐", "保存", "返回"))

    def open_outfit_editor(self):
        self.fixture_home("穿搭")
        root, _ = self.dump()
        parents, viewport = self.parents(root), self.interactive_viewport()
        images = [node for node in root.iter("node") if node.get("content-desc") == "穿搭照片"]
        for image in images:
            x, y = center(image)
            card = image
            while card.get("clickable") != "true" and card in parents:
                card = parents[card]
            if card.get("clickable") == "true" and viewport[0] <= x <= viewport[2] and viewport[1] <= y <= viewport[3]:
                self.tap_node(image)
                self.expect(present=("编辑穿搭", "保存", "返回"))
                return
        raise AssertionError("No visible fixture outfit photo opens its editor")

    def open_event_editor(self):
        row = next(row for row in self.fixture["events"] if row["id"] == "iosqa-moment-text")
        self.home("点滴")
        # Use the existing notification target to locate a known fixture directly;
        # the gallery does not need to search through the entire long timeline.
        self.notify("event", row["date"], row["id"])
        self.tap_content(row["title"])
        self.expect(present=("编辑点滴", "保存", "返回"))

    def gallery(self, theme):
        self.normal(theme)
        captured = []
        for kind in ("outfit", "meal", "moment", "anniversary"):
            self.open_gallery_form(kind, theme)
            if kind == "meal":
                captured.append("meal-chooser")
            self.assert_clean_copy()
            self.snapshot(theme + "-add-" + kind)
            captured.append("add-" + kind)
            self.back()  # Empty forms have no unsaved content and never save.
        self.home()
        self.open_settings()
        self.snapshot(theme + "-settings-home")
        captured.append("settings-home")
        for index, title in enumerate(MODULES):
            self.tap(title, scroll=True)
            self.expect(present=(title, "返回"))
            self.assert_clean_copy()
            self.snapshot(theme + f"-settings-module-{index + 1}")
            captured.append(title)
            self.tap("返回")
            self.expect(present=("设置", "我的信息", "外观"))
        for kind, opener in (("outfit", self.open_outfit_editor),
                             ("event", self.open_event_editor), ("meal", self.open_meal_editor)):
            opener()
            self.assert_clean_copy()
            self.snapshot(theme + "-edit-" + kind)
            captured.append("edit-" + kind)
        # The current page is the existing own breakfast editor. Only cancel.
        self.form_start("内容")
        self.tap_node(self.field("内容"))
        self.shell("input", "text", "GALLERY_QA_NOTE")
        self.hide_keyboard()
        self.back()
        self.expect(present=("放弃修改？", "继续编辑", "放弃修改"))
        self.snapshot(theme + "-unsaved-confirm-cancelled")
        self.tap("继续编辑")
        self.expect(present=("记录早餐", "保存"), absent=("放弃修改？",))
        self.tap("删除这条记录", scroll=True)
        self.expect(present=("删除", "取消"))
        self.snapshot(theme + "-delete-confirm-cancelled")
        self.tap("取消")
        self.expect(present=("记录早餐", "保存"))
        captured.extend(("delete-confirm-cancelled", "unsaved-confirm-cancelled"))
        # The next setup/final restore resets only this synthetic Activity draft.
        return {"theme": theme, "captured": captured, "saved_or_deleted": False}

    def run_checks(self, groups):
        try:
            self.preflight()
            self.baseline()
            if "state" in groups or "draft" in groups:
                self.check("system-theme-preserves-open-draft", self.test_system_draft)
            if "state" in groups:
                self.check("system-theme-preserves-tab-and-scroll", self.test_system_home_position)
            if "state" in groups or "position" in groups:
                self.check("manual-theme-settings-return-preserves-scroll", self.test_manual_home_position)
            if "gallery" in groups:
                for theme in ("light", "dark"):
                    self.check("gallery-" + theme, lambda theme=theme: self.gallery(theme))
        except KeyboardInterrupt:
            self.report["fatal_error"] = "Interrupted; restoring settings and appearance"
        except Exception as error:
            self.report["fatal_error"] = type(error).__name__ + ": " + str(error)
        finally:
            self.restore()
            if self.fixture_hash:
                try:
                    _, digest = self.fixture_digest()
                    self.report["fixture_digest_after"] = digest
                    require(digest == self.fixture_hash, "Synthetic records changed during a no-save/no-delete run")
                    self.report["fixture_unchanged"] = True
                except Exception as error:
                    self.report["fixture_unchanged"] = False
                    self.report["fixture_integrity_error"] = str(error)
            self.report["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
            self.report["passed"] = bool(self.report["checks"]) and not self.report.get("fatal_error") and all(
                check["status"] == "passed" for check in self.report["checks"]
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
    parser.add_argument("--checks", nargs="+", choices=CHECKS, default=["state"])
    arguments = parser.parse_args(argv)
    return StateQa(arguments.adb, arguments.output).run_checks(arguments.checks)


if __name__ == "__main__":
    sys.exit(main())
