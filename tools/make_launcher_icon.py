# -*- coding: utf-8 -*-
"""把用户提供的毛毡玩偶合照切成 Android adaptive icon 需要的各密度前景 PNG。

v2.5.0 版说明：
1. 新源图是满幅照片（无白边），不需要再找内容包围盒；直接按中心裁成正方形。
2. adaptive icon 可见安全区是中心 2/3 直径的圆。源图主体（两个玩偶的脸）都集中在
   画面中心偏下区域，满幅铺满 108dp 画布后，圆形裁切只会裁掉四角的天空/栅栏，
   人物主体完整保留、不变形。
3. 为了让主体在小尺寸下更清晰，先对中心区域做 8% 的内缩取景（相当于轻微放大主体），
   再铺满画布。
4. 背景色取画面上缘天空/树叶的平均暖色，供少数需要底层色兜底的场景。
"""
import os
from PIL import Image

SRC = r"C:\Users\29172\.workbuddy\clipboard-images\clipboard-2026-10-28T05-04-24-865Z-901fb826.jpg"
RES = r"E:\project\AI_seession\zcode\TheCutestGirl\app\src\main\res"

# 每个密度对应的前景像素尺寸（108dp * 密度倍数）
DENSITIES = {
    "mipmap-mdpi": 108,      # 1.0x
    "mipmap-hdpi": 162,      # 1.5x
    "mipmap-xhdpi": 216,     # 2.0x
    "mipmap-xxhdpi": 324,    # 3.0x
    "mipmap-xxxhdpi": 432,   # 4.0x
}


def sample_background(img: Image.Image):
    """取画面上缘区域的平均色作为背景兜底色（天空+树叶的暖调）。"""
    w, h = img.size
    box = img.crop((0, 0, w, h // 10)).convert("RGB")
    small = box.resize((1, 1), Image.LANCZOS)
    return small.getpixel((0, 0))


def main():
    if not os.path.exists(SRC):
        raise SystemExit(f"找不到源图：{SRC}")
    img = Image.open(SRC).convert("RGB")
    print("源图尺寸:", img.size)

    # 中心 8% 内缩取景（轻微放大主体，小图标下更清晰）
    w, h = img.size
    inset_x = int(w * 0.08)
    inset_y = int(h * 0.08)
    view = img.crop((inset_x, inset_y, w - inset_x, h - inset_y))

    # 按中心裁成正方形（取短边，不拉伸变形）
    side = min(view.size)
    cx, cy = view.width // 2, view.height // 2
    square = view.crop((cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2))

    bg = sample_background(img)
    print("采样背景色: #%02X%02X%02X" % bg)

    for folder, size in DENSITIES.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        resized = square.resize((size, size), Image.LANCZOS)
        out_path = os.path.join(out_dir, "ic_launcher_foreground.png")
        resized.save(out_path, "PNG")
        print(f"已生成 {folder}/ic_launcher_foreground.png  {size}x{size}")

    print("背景色（写进 colors.xml）: #%02X%02X%02X" % bg)


if __name__ == "__main__":
    main()
