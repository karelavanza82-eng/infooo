# Integrasi CleanBar ke Hoisly Launcher

- Mesin status bar CleanBar dijalankan langsung dari package `com.hoisly.launcher`.
- Permintaan izin Shizuku ditujukan kepada Hoisly Launcher, bukan aplikasi CleanBar terpisah.
- Halaman `Hide Status Bar` menjadi subhalaman baru di Settings > Bilah Status.
- Toggle lama yang mengubah immersive status bar dari MainActivity telah dihapus.
- Controller baru menjalankan urutan perintah CleanBar, menyimpan nilai awal `policy_control` dan `icon_blacklist`, serta menyediakan pemulihan.
- Provider Shizuku dan dependency Shizuku Hoisly yang sudah ada digunakan kembali; tidak ada provider/aplikasi CleanBar kedua.

Build Android belum diverifikasi pada lingkungan ini. Jalankan workflow GitHub Actions untuk validasi kompilasi dan uji pada perangkat Android sebelum rilis.
