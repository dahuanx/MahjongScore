# -*- coding: utf-8 -*-
"""生成麻将计分器安装二维码（纯码 + 发群卡片）。用法：
   python make_qr.py <url> <版本号> <输出前置名>
"""
import sys, os
import qrcode
from qrcode.constants import ERROR_CORRECT_M
from PIL import Image, ImageDraw, ImageFont

URL = sys.argv[1] if len(sys.argv) > 1 else "http://182.92.191.33:9100/app/latest.apk"
VER = sys.argv[2] if len(sys.argv) > 2 else "1.0.0"
OUT = sys.argv[3] if len(sys.argv) > 3 else "二维码-安装"

INK = (25, 19, 34)
PAPER = (255, 247, 236)
LEMON = (255, 210, 63)
RED = (255, 61, 127)

def font(size, bold=True):
    for p in ("C:/Windows/Fonts/msyhbd.ttc", "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size, index=0)
            except Exception:
                continue
    return ImageFont.load_default()

# ---------- 1. 纯二维码 ----------
qr = qrcode.QRCode(version=None, error_correction=ERROR_CORRECT_M, box_size=20, border=4)
qr.add_data(URL)
qr.make(fit=True)
img = qr.make_image(fill_color="black", back_color="white").convert("RGB")
side = 1000
img = img.resize((side, side), Image.NEAREST)
img.save(f"{OUT}-纯码.png")
print("pure:", f"{OUT}-纯码.png", img.size, "modules:", qr.modules_count, "ver:", qr.version)

# ---------- 2. 发群卡片 ----------
W, H = 900, 1050
card = Image.new("RGB", (W, H), PAPER)
d = ImageDraw.Draw(card)

# 外框（硬阴影 + 墨线，呼应 App 的贴纸风格）
d.rectangle([26 + 10, 26 + 10, W - 26 + 10, H - 26 + 10], fill=INK)   # 阴影
d.rectangle([26, 26, W - 26, H - 26], fill=(255, 255, 255), outline=INK, width=6)

# 顶部色条
d.rectangle([26, 26, W - 26, 96], fill=LEMON, outline=INK, width=6)

d.text((64, 44), "麻将计分器", font=font(38), fill=INK)
vf = font(24)
tw = d.textlength(f"安卓版 v{VER}", font=vf)
d.text((W - 64 - tw, 54), f"安卓版 v{VER}", font=vf, fill=INK)

# 二维码
q = qrcode.QRCode(error_correction=ERROR_CORRECT_M, box_size=12, border=2)
q.add_data(URL)
q.make(fit=True)
qimg = q.make_image(fill_color="black", back_color="white").convert("RGB")
qs = 560
qimg = qimg.resize((qs, qs), Image.NEAREST)
qx, qy = (W - qs) // 2, 140
d.rectangle([qx - 14, qy - 14, qx + qs + 14, qy + qs + 14], fill=(255, 255, 255), outline=INK, width=5)
card.paste(qimg, (qx, qy))

# 说明文字
d.text((W // 2, qy + qs + 42), "微信扫一扫 → 右上角「在浏览器打开」", font=font(27), fill=INK, anchor="mm")
d.text((W // 2, qy + qs + 88), "也可在浏览器手动输入下面的地址", font=font(22), fill=(90, 82, 105), anchor="mm")

# 地址条
addr = URL.replace("http://", "")
ab = font(26)
aw = d.textlength(addr, font=ab)
bx0, bw = (W - aw - 60) // 2, aw + 60
by = qy + qs + 118
d.rectangle([bx0, by, bx0 + bw, by + 58], fill=PAPER, outline=INK, width=4)
d.text((W // 2, by + 29), addr, font=ab, fill=INK, anchor="mm")

# 底部提示
d.text((W // 2, by + 108), "装好后打开 App，输入昵称即可建房开局", font=font(22), fill=(90, 82, 105), anchor="mm")
d.text((W // 2, by + 148), "纯积分记账 · 不涉及真实货币", font=font(20), fill=RED, anchor="mm")

card.save(f"{OUT}-卡片.png")
print("card:", f"{OUT}-卡片.png", card.size)
