# 📺 Panduan Pemasangan Padle Score (Smart TV & Remote)

Panduan ini menjelaskan langkah-langkah untuk memasang aplikasi **Padle Score** pada Smart TV (Android TV), melakukan konfigurasi **ESP8266 (WiFi Remote)**, dan menggunakan **Web Remote** melalui scan QR Code.

---

## 🛠️ Bagian 1: Pemasangan pada Smart TV / Android TV Box

Agar papan skor tampil maksimal dan berjalan otomatis, ikuti langkah berikut:

### 1. Instalasi APK
1. Siapkan file APK (misal: `Padle_Score_v2.x.x.apk`).
2. Masukkan ke dalam Flashdisk dan colokkan ke Smart TV.
3. Buka **File Manager** pada TV dan instal APK tersebut. 
   *(Pastikan opsi "Unknown Sources" sudah diaktifkan di pengaturan keamanan TV).*

### 2. Setup Kiosk Mode (Home Launcher)
Agar aplikasi langsung terbuka saat TV dinyalakan:
1. Saat menekan tombol **Home** pada remote TV, akan muncul pilihan launcher.
2. Pilih **Padle Score**, lalu pilih **"Always" / "Selalu"**.
3. Aplikasi kini berfungsi sebagai *dashboard* permanen.

### 3. Izin Aplikasi (Permissions)
Pastikan memberikan izin berikut saat aplikasi pertama kali dibuka:
- **Storage/Files**: Untuk menyimpan riwayat pertandingan dan foto pemain.
- **Microphone**: Jika fitur pengumuman suara (Announcer) diaktifkan.

---

## 🔌 Bagian 2: Konfigurasi ESP8266 (WiFi IR Sensor)

ESP8266 bertindak sebagai *touchless button* menggunakan sensor IR. Alat ini harus terhubung ke WiFi yang sama dengan Smart TV.

### 1. Masuk ke AP Mode (Setup Mode)
Jika alat baru pertama kali digunakan atau ingin mengganti koneksi WiFi:
1. Matikan daya ESP8266.
2. **Penting:** Tutup/halangi **KEDUA sensor IR** (Kiri & Kanan) secara bersamaan menggunakan tangan atau kertas.
3. Nyalakan daya ESP8266 sambil tetap menghalangi kedua sensor.
4. Tunggu hingga lampu LED pada ESP8266 menyala diam (Steady ON). Lepaskan tangan Anda.
5. Alat sekarang berada dalam **AP Mode (Hotspot)**.

### 2. Mengatur WiFi & IP TV
1. Gunakan HP untuk mencari WiFi bernama: **`Paddle-Score-Setup`**.
2. Hubungkan ke WiFi tersebut (tanpa password).
3. Jika halaman pengaturan tidak muncul otomatis, buka browser dan ketik: `192.168.4.1`.
4. Isi data berikut:
   - **WiFi SSID**: Nama WiFi di lokasi (sama dengan WiFi TV).
   - **WiFi Password**: Kata sandi WiFi.
   - **TV Box IP**: Alamat IP yang muncul di layar TV (pojok kanan bawah).
   - **TV Box Port**: Default adalah `8888`.
5. Klik **SAVE SETTINGS**. Alat akan restart dan mencoba terhubung.

### 3. Indikator LED & Suara
- **Melodi Naik (Beep 3x)**: Berhasil terhubung ke aplikasi di TV.
- **LED Ready Berkedip Lambat**: Koneksi stabil dan siap digunakan.
- **LED Ready Berkedip Cepat**: Gagal terhubung ke WiFi atau IP TV salah.

---

## 📱 Bagian 3: Penggunaan Web Remote (QR Code)

Selain menggunakan sensor IR, Anda bisa mengontrol skor langsung dari HP siapa saja tanpa instal aplikasi tambahan.

### 1. Menampilkan QR Code
1. Pastikan Smart TV dan HP berada di jaringan WiFi yang sama.
2. QR Code akan muncul otomatis di layar TV (Bagian bawah) saat skor dalam kondisi **0 - 0** (awal pertandingan atau setelah reset).

### 2. Scanning & Kontrol
1. Buka aplikasi kamera atau pemindai QR di HP.
2. Scan QR Code yang ada di TV.
3. Klik link yang muncul (misal: `http://192.168.1.15:8888`).
4. Halaman kontrol remote akan terbuka di browser HP.
5. Anda sekarang bisa menambah/mengurangi poin, mengganti nama tim, dan melakukan reset langsung dari HP.

---

## 💡 Tips Penggunaan
- **Reset Skor**: Tahan (Long Press) kedua sensor IR selama 2 detik atau gunakan tombol Reset di Web Remote.
- **Koreksi Skor**: Tahan salah satu sensor IR selama 1.5 detik untuk mengurangi poin jika terjadi kesalahan input.
- **Ganti Nama Tim**: Klik pada nama tim di Web Remote (HP) untuk mengubah nama sesuai keinginan.

---
*Dokumentasi disusun untuk Padle Score Scoreboard System.*
