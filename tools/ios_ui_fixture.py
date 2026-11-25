"""Synthetic UI fixtures for the disposable iOS UI verification package only.

Default invocation performs read-only preflight checks. Pass --seed to stop the
isolated app, write synthetic gradient PNGs and prefix-scoped records, then
restart that same app. No production package, preferences, database replacement,
private photographs, downloads, or third-party Python packages are involved.

Example (run only after the empty-state smoke test has finished):
    python tools/ios_ui_fixture.py --seed
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import hashlib
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zlib


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.thecutestgirl.app.verification.iosui"
ACTIVITY = PACKAGE + "/com.thecutestgirl.app.MainActivity"
DATABASE = "databases/the_cutest_girl.db"
SQLITE = "/system/bin/sqlite3"
ID_PREFIX = "iosqa-"
PHOTO_PREFIX = "iosqa_"
PARTNER = "iosqa-partner"
TABLES = ("outfits", "meals", "events")
SCHEMA_FILE = ROOT / "app/schemas/com.thecutestgirl.app.data.AppDatabase/6.json"
MISSING_PHOTO = "iosqa_missing_for_placeholder.png"
MY_NAME = "合成测试里的我和一个非常非常长的昵称"
PARTNER_NAME = "合成测试里的对方和一个需要自动换行的很长很长的昵称"


def find_adb(value: str | None) -> str:
    if value:
        candidate = Path(value)
        if candidate.is_file():
            return str(candidate.resolve())
        resolved = shutil.which(value)
        if resolved:
            return resolved
        raise ValueError("The supplied adb executable was not found.")
    resolved = shutil.which("adb")
    if resolved:
        return resolved
    sdk_paths = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk_paths.append(line.partition("=")[2].replace("\\:", ":").replace("\\\\", "\\"))
    for sdk in sdk_paths:
        if sdk:
            for name in ("adb.exe", "adb"):
                candidate = Path(sdk) / "platform-tools" / name
                if candidate.is_file():
                    return str(candidate.resolve())
    raise ValueError("adb was not found; pass --adb or configure ANDROID_HOME.")


class Device:
    def __init__(self, adb: str, serial: str):
        # A phone serial cannot be selected, even accidentally.
        if not re.fullmatch(r"emulator-\d+", serial):
            raise ValueError("Only an emulator-NNNN serial is accepted.")
        self.adb = adb
        self.serial = serial

    def run(self, *args: str, data: bytes | None = None, discard: bool = False,
            allow_missing: bool = False) -> bytes | None:
        result = subprocess.run(
            [self.adb, "-s", self.serial, *args], input=data,
            stdout=subprocess.DEVNULL if discard else subprocess.PIPE,
            stderr=subprocess.PIPE, timeout=45,
        )
        if result.returncode:
            error = result.stderr.decode("utf-8", errors="replace")
            # Missing optional preference files are expected on a fresh app.
            if allow_missing and "No such file or directory" in error:
                return None
            raise RuntimeError("adb failed for the isolated verification package: " + error.strip())
        return result.stdout or b""

    def app(self, *args: str, **kwargs) -> bytes | None:
        # shell -T preserves binary stdin without a PTY, and shell protocol v2
        # preserves remote exit status/stderr. exec-out loses that exit status.
        return self.run("shell", "-T", "run-as", PACKAGE, *args, **kwargs)

    def sql(self, text: str, *, readonly: bool = True) -> str:
        arguments = [SQLITE, "-batch", "-bail", "-noheader"]
        if readonly:
            arguments.append("-readonly")
        arguments.append(DATABASE)
        return self.app(*arguments, data=(text + "\n").encode("utf-8")).decode("utf-8").strip()

    def prefs(self, name: str, *, required: bool = False) -> dict[str, str]:
        # File names are constants supplied by this script, never a user path.
        if name not in {"identity", "settings", "dav_secret"}:
            raise ValueError("Unexpected preference file.")
        raw = self.app("cat", "shared_prefs/" + name + ".xml", allow_missing=not required)
        if raw is None:
            return {}
        return {entry.attrib["name"]: entry.text or entry.attrib.get("value", "")
                for entry in ET.fromstring(raw) if "name" in entry.attrib}

    def assert_no_cloud(self) -> None:
        settings = self.prefs("settings")
        secret = self.prefs("dav_secret")
        if any(value.strip() for value in (
            settings.get("webdav_user", ""), settings.get("webdav_pass", ""),
            secret.get("webdav_pass", ""),
        )):
            raise ValueError("Refusing to seed: this isolated app has cloud credentials configured.")


def read_schema() -> dict:
    schema = json.loads(SCHEMA_FILE.read_text(encoding="utf-8"))["database"]
    if schema["version"] != 6:
        raise ValueError("This fixture requires the inspected Room schema version 6.")
    entities = {entity["tableName"]: entity for entity in schema["entities"]}
    if not set(TABLES).issubset(entities):
        raise ValueError("Expected record tables are missing from the repository schema.")
    return entities


def counts(device: Device, *, synthetic: bool) -> dict[str, int]:
    operator = "GLOB" if synthetic else "NOT GLOB"
    return {table: int(device.sql(
        f'SELECT count(*) FROM "{table}" WHERE "id" {operator} \'iosqa-*\';'
    )) for table in TABLES}


def preflight(device: Device, schema: dict) -> str:
    if device.run("get-state").decode().strip() != "device":
        raise ValueError("The requested emulator is not ready.")
    if device.run("shell", "getprop", "ro.kernel.qemu").decode().strip() != "1":
        raise ValueError("Refusing a target that does not identify as an emulator.")
    app_directory = device.app("pwd").decode().strip()
    if app_directory not in {"/data/user/0/" + PACKAGE, "/data/data/" + PACKAGE}:
        raise ValueError("run-as did not resolve to the exact isolated package.")
    device.assert_no_cloud()
    device_id = device.prefs("identity", required=True).get("device_id", "").strip()
    if not device_id or device_id in {"local", PARTNER}:
        raise ValueError("The isolated app must first create a valid local device identity.")
    if device.sql("PRAGMA user_version;") != "6":
        raise ValueError("The installed app database is not schema version 6.")
    for table in TABLES:
        actual = []
        for line in device.sql(f'PRAGMA table_info("{table}");').splitlines():
            parts = line.strip().split("|")
            actual.append((parts[1], parts[2].upper(), int(parts[3])))
        expected = [(field["columnName"], field["affinity"], int(field["notNull"]))
                    for field in schema[table]["fields"]]
        if actual != expected:
            raise ValueError("Installed table does not match the inspected schema: " + table)
    return device_id


def gradient_png(seed: int, width: int = 480, height: int = 640) -> bytes:
    """Generate an abstract RGB sample; it cannot contain a personal photo."""
    colors = ((221, 193, 204), (185, 205, 211), (213, 207, 184), (194, 190, 218))
    top = colors[seed % len(colors)]
    bottom = colors[(seed + 1) % len(colors)]
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # PNG scanline filter: None.
        progress = y / max(height - 1, 1)
        for x in range(width):
            light = 14 * x / max(width - 1, 1)
            # A quiet geometric disc makes thumbnail crop differences visible.
            disc = (x - width * 0.62) ** 2 + (y - height * 0.38) ** 2 < (width * 0.21) ** 2
            for start, end in zip(top, bottom):
                value = start * (1 - progress) + end * progress + light + (14 if disc else 0)
                raw.append(max(0, min(255, round(value))))

    def chunk(kind: bytes, payload: bytes) -> bytes:
        checksum = zlib.crc32(kind + payload) & 0xFFFFFFFF
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", checksum)

    return (b"\x89PNG\r\n\x1a\n" +
            chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) +
            chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b""))


def make_fixture(schema: dict, device_id: str, day: dt.date) -> tuple[dict, dict]:
    records = {table: [] for table in TABLES}
    photos = {}
    now = int(time.time() * 1000)
    long_note = ("这是一段用于界面换行检查的合成记录：浅灰、玫粉和自然光都很好看，"
                 "希望长文字在小屏幕和大字体下仍然清晰。") * 3
    long_reply = ("这是对方的合成评论，我们各自保留自己的文字；"
                  "多写几句来检查作者昵称、评论区域和操作按钮的排列。") * 2

    def row(table: str, key: str, own: bool, **values):
        result = {field["columnName"]: (0 if field["affinity"] == "INTEGER" else
                  ("" if field["notNull"] else None)) for field in schema[table]["fields"]}
        owner, name = (device_id, MY_NAME) if own else (PARTNER, PARTNER_NAME)
        result.update(id=ID_PREFIX + key, origin=owner, originName=name,
                      noteDevice=owner, noteName=name,
                      reply=long_reply, replyName=PARTNER_NAME if own else MY_NAME,
                      createdAt=now + len(records[table]), updatedAt=now,
                      noteUpdatedAt=now, replyUpdatedAt=now, dirty=0)
        result.update(values)
        records[table].append(result)

    # Eight distinct dates, including one deliberately unavailable photo.
    for index in range(8):
        name = MISSING_PHOTO if index == 7 else f"iosqa_outfit_{index}.png"
        if index != 7:
            photos[name] = gradient_png(index)
        row("outfits", f"outfit-{index}", own=index % 2 == 0,
            date=(day - dt.timedelta(days=index)).isoformat(), photoPath=name, note=long_note)

    # Both people have breakfast, lunch and dinner on the current UI date.
    for meal_type, label in enumerate(("早餐", "午餐", "晚餐")):
        name = f"iosqa_meal_{meal_type}.png"
        photos[name] = gradient_png(meal_type + 8, width=640, height=480)
        for own in (True, False):
            row("meals", f"meal-{meal_type}-{'mine' if own else 'partner'}", own=own,
                date=day.isoformat(), mealType=meal_type, photoPath=name,
                note=label + "合成样例。" + long_note)

    title = "这一条很长很长的合成标题用来检查大字体下的换行和末尾省略是否自然"
    row("events", "anniversary-past", own=True, type=1, date="1900-01-01",
        title="五位数纪念日 · " + title, description=long_note)
    row("events", "anniversary-future", own=False, type=1,
        date=(day + dt.timedelta(days=45)).isoformat(), title="未来纪念日 · " + title,
        description=long_note)
    photos["iosqa_moment.png"] = gradient_png(12, width=640, height=480)
    row("events", "moment-photo", own=False, type=0, date=day.isoformat(),
        title="带图点滴 · " + title, description=long_note, photoPath="iosqa_moment.png")
    row("events", "moment-text", own=True, type=0,
        date=(day - dt.timedelta(days=1)).isoformat(),
        title="纯文字点滴 · " + title, description=long_note)
    return records, photos


def sql_literal(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    if not isinstance(value, str) or "\x00" in value:
        raise ValueError("Unsupported fixture SQL value.")
    return "'" + value.replace("'", "''") + "'"


def seed_sql(schema: dict, records: dict) -> str:
    statements = [".timeout 5000", "PRAGMA foreign_keys=ON;", "BEGIN IMMEDIATE;"]
    for table in TABLES:
        columns = [field["columnName"] for field in schema[table]["fields"]]
        column_sql = ", ".join('"' + name + '"' for name in columns)
        updates = ", ".join(f'"{name}"=excluded."{name}"' for name in columns if name != "id")
        for record in records[table]:
            if not record["id"].startswith(ID_PREFIX) or record["dirty"] != 0:
                raise ValueError("Refusing any unscoped or dirty fixture record.")
            photo = record.get("photoPath")
            if photo and not re.fullmatch(r"iosqa_[a-z0-9_]+\.png", photo):
                raise ValueError("Refusing a photo path outside the synthetic namespace.")
            values = ", ".join(sql_literal(record[name]) for name in columns)
            statements.append(f'INSERT INTO "{table}" ({column_sql}) VALUES ({values}) '
                              f'ON CONFLICT("id") DO UPDATE SET {updates};')
    statements.append("COMMIT;")
    return "\n".join(statements)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", action="store_true", help="Write only synthetic iosqa fixtures.")
    parser.add_argument("--serial", default="emulator-5556", help="Emulator serial; phones are rejected.")
    parser.add_argument("--adb", help="Optional path to adb; otherwise use PATH or the local SDK.")
    parser.add_argument("--date", type=dt.date.fromisoformat,
                        help="Optional fixture date (YYYY-MM-DD); defaults to the emulator date.")
    args = parser.parse_args()
    device = Device(find_adb(args.adb), args.serial)
    schema = read_schema()
    device_id = preflight(device, schema)
    if not args.seed:
        print(json.dumps({"package": PACKAGE, "schema": 6, "mode": "read-only",
                          "synthetic_records": counts(device, synthetic=True)}, ensure_ascii=False))
        print("Preflight passed. Pass --seed only after the empty-state smoke test has finished.")
        return 0

    day = args.date or dt.date.fromisoformat(device.run("shell", "date", "+%F").decode().strip())
    records, photos = make_fixture(schema, device_id, day)
    sql = seed_sql(schema, records)
    untouched_before = counts(device, synthetic=False)
    missing = device.app("ls", "files/photos/" + MISSING_PHOTO, allow_missing=True)
    if missing is not None:
        raise ValueError("The deliberately missing synthetic photo already exists; refusing to remove it.")

    # The only process stopped is the hardcoded disposable verification package.
    device.run("shell", "am", "force-stop", PACKAGE)
    device.assert_no_cloud()
    device.app("mkdir", "-p", "files/photos")
    local_photos = ROOT / ".verification" / "ui-refinement" / "fixture-photos"
    local_photos.mkdir(parents=True, exist_ok=True)
    for filename, image in photos.items():
        if not re.fullmatch(r"iosqa_[a-z0-9_]+\.png", filename):
            raise ValueError("Unsafe synthetic photo name.")
        # Windows adb's shell stdin can treat PNG control bytes as EOF. push
        # transfers binary data intact, then run-as copies into this app's sandbox.
        local_photo = local_photos / filename
        local_photo.write_bytes(image)
        remote_photo = "/data/local/tmp/" + filename
        device.run("push", str(local_photo), remote_photo)
        device.app("cp", remote_photo, "files/photos/" + filename)
        actual_hash = device.app("sha256sum", "files/photos/" + filename).decode().split()[0]
        if actual_hash != hashlib.sha256(image).hexdigest():
            raise RuntimeError("Synthetic photo transfer integrity check failed: " + filename)
    # Write through SQLite into the existing database; never replace it or remove WAL files.
    device.sql(sql, readonly=False)
    if counts(device, synthetic=False) != untouched_before:
        raise RuntimeError("Unexpected change to non-fixture record counts; app remains stopped.")
    synthetic_counts = counts(device, synthetic=True)
    for table in TABLES:
        for record in records[table]:
            if device.sql(f'SELECT count(*) FROM "{table}" WHERE "id"='
                          f'{sql_literal(record["id"])} AND "dirty"=0;') != "1":
                raise RuntimeError("A synthetic record was not written cleanly; app remains stopped.")
    device.run("shell", "am", "start", "-W", "-n", ACTIVITY)
    print(json.dumps({"package": PACKAGE, "date": day.isoformat(), "seeded": {
        table: len(records[table]) for table in TABLES}, "synthetic_records": synthetic_counts,
        "synthetic_pngs": len(photos), "all_fixture_dirty": 0,
        "preferences_changed": False}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired, ET.ParseError) as error:
        print("Fixture aborted: " + str(error), file=sys.stderr)
        raise SystemExit(1)
