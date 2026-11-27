# HOISLY v8.8 — Liquid Glass fix (Android 13 / 15 / 16 / 17)

## Akar masalah
Gate versi (`LIQUID_GLASS_MIN_SDK = 33`) sudah benar. Yang gagal adalah pembacaan wallpaper:
`WallpaperManager.getDrawable()` melempar SecurityException di Android 11+ (praktis wajib di 13+)
untuk app tanpa All files access. Kode lama menelan error itu (`runCatching{}.getOrNull()`),
sumber glass jadi null, lalu semuanya diam-diam turun ke Frosted.
Patch v8.7 malah menghapus izin + membuat `hasAccess()` selalu true, sehingga prompt izin
tidak pernah muncul. Di Android 14 (device tes POCO X5) jalurnya kebetulan lolos.

## Perubahan
- Manifest: `MANAGE_EXTERNAL_STORAGE` dikembalikan (opsional, hanya untuk baca wallpaper).
- `WallpaperBlur.hasAccess()` kembali jujur: `isExternalStorageManager()` atau pembacaan sudah pernah berhasil.
- `readWallpaper()`: coba `drawable` lalu `peekDrawable`, tangkap SecurityException, log alasan ke logcat.
- Bitmap HARDWARE otomatis disalin ke software sebelum digambar ke Canvas.

## Cara verifikasi di HP (Android 13/15/16/17)
1. Install, buka Hoisly, beri "All files access" saat diminta (atau lewat Settings > Finish setup).
2. Cek log: `adb logcat -s HoislyGlass`
   - "Wallpaper unreadable ... SecurityException" = izin belum aktif.
   - "Liquid Glass shader unavailable" = GPU/driver menolak AGSL (masalah lain, kirim log-nya).

Belum dibuild/dites (tidak ada akses jaringan untuk Gradle di sini).

## Diagnostik di dalam app
Settings > layar Glass (saat Blur aktif) menampilkan blok "Glass diagnostics": versi Android,
izin wallpaper, apakah wallpaper termuat, apakah shader AGSL ter-compile, plus pesan error.
Tester cukup kirim screenshot blok ini.
