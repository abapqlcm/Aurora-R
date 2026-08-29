# Aurora-R

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="96" alt="Aurora-R">
</p>

<p align="center">
  <b>اپلیکیشن اندروید با رابط گرافیکی (UI) بر پایه‌ی هسته‌ی Aether</b><br>
  تانل دور زدن سانسور با حالت <b>TUN</b> (کل دستگاه) • تم مشکی و طلایی
</p>

---

## ویژگی‌ها

- **حالت TUN واقعی**: کل ترافیک دستگاه از تانل عبور می‌کند (نه فقط پروکسی) — با `VpnService` + `hev-socks5-tunnel`
- **هسته‌ی Aether v1.8.0**: کتابخانه‌ی `libaether.so` (Rust) از طریق JNI فراخوانی می‌شود
- **اسکن خودکار endpoint**: هسته بهترین سرور را پیدا می‌کند (حالت‌های سریع/متعادل/دقیق)
- **سرور شخصی**: امکان وارد کردن IP:PORT دلخواه و ذخیره‌ی چند سرور
- **پروتکل‌ها**: MASQUE (HTTP/3)، WireGuard، Gool (WARP-in-WARP)
- **مبهم‌سازی ترافیک** (noize): پروفایل‌های firewall / balanced / aggressive
- **آمار زنده**: آپلود و دانلود لحظه‌ای در صفحه‌ی اصلی
- **رابط مشکی و طلایی** با دکمه‌ی اتصال انیمیشنی

## سازنده

**Rez** — [@iprez](https://t.me/iprez) (تلگرام)

> این پروژه برای استفاده‌ی شخصی ساخته شده است.

## معماری

```
UI (Jetpack Compose)
   │
   ▼
AuroraVpnService (VpnService + TUN fd)
   │
   ├─ TunBridge → libhev-socks5-tunnel.so  (TUN ⇄ SOCKS5)
   │
   └─ AetherCore → libaether.so  (C API / JSON)
                      │
                      └─ SOCKS5 روی 127.0.0.1:1819
```

جریان: کاربر دکمه را می‌زند → اپ مجوز VPN می‌گیرد → هسته‌ی Aether روی
SOCKS5 محلی اجرا می‌شود → `tun2socks` کل ترافیک TUN را می‌گیرد و به SOCKS5
می‌فرستد → ترافیک از تانل رمز شده خارج می‌شود. خودِ اپ از تانل مستثنا شده
تا حلقه ایجاد نشود.

## بیلد

هر پوش به شاخه‌ی `main` بیلد APK را در **GitHub Actions** شروع می‌کند:

1. کراس‌کامپایل `libaether.so` (Rust + NDK r26d + cargo-ndk)
2. بیلد `libhev-socks5-tunnel.so` (NDK ndk-build)
3. اسمبل کردن APK نهایی با Gradle

آرتیفکت `Aurora-R-APK` در بخش Actions قابل دانلود است.

## لایسنس

هسته‌ی Aether تحت [AGPL-3.0](https://github.com/CluvexStudio/Aether) منتشر
می‌شود. لایه‌ی UI و پل JNI این پروژه نیز طبق همان مجوز در دسترس است.
نام «Aether» متعلق به پروژه‌ی اصلی است؛ این اپ یک رابط مستقل با نام Aurora-R
است و نباید به‌عنوان محصول رسمی Aether معرفی شود.
