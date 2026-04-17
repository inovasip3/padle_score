# Panduan Padel Scoreboard - ESP8266 Controller

Padel Scoreboard Controller adalah perangkat berbasis ESP8266 yang menggunakan sensor IR (Infrared) sebagai tombol tanpa sentuh untuk mencatat skor pertandingan Padel secara otomatis. Perangkat ini mengirimkan perintah ke aplikasi Android TV Box melalui koneksi WiFi.

## Spesifikasi Perangkat
- **Microcontroller**: ESP8266 (Wemos D1 Mini atau sejenisnya).
- **Sensor**: 2x IR Line Detection Module (TCRT5000 / HW-201).
- **Feedback**: Active Buzzer dan LED indikator status.
- **Koneksi**: WiFi 2.4GHz.
- **Protokol**: HTTP GET Command.

## Koneksi Pin (Wiring)

| Komponen | Pin ESP8266 | Nama Pin (GPIO) | Fungsi |
| :--- | :--- | :--- | :--- |
| **IR Sensor KIRI** | D1 | GPIO5 | Tambah Skor Tim A |
| **IR Sensor KANAN** | D2 | GPIO4 | Tambah Skor Tim B |
| **Buzzer (Active)** | D5 | GPIO14 | Feedback Suara |
| **LED Tim A** | D6 | GPIO12 | Indikator Skor Berhasil (Kiri) |
| **LED Tim B** | D7 | GPIO13 | Indikator Skor Berhasil (Kanan) |
| **LED Ready** | D8 | GPIO15 | Indikator Status Koneksi/Ready |
| **LED Internal** | D4 | GPIO2 | Status Sistem Utama |

> [!NOTE]
> **Sensor IR**: Sambungkan VCC ke 3.3V, GND ke GND, dan OUT ke pin Dx yang sesuai. Sesuaikan sensitivitas sensor menggunakan potensiometer onboard hingga dapat mendeteksi tangan pada jarak ~10-15cm.

---

## Panduan Pengaturan (Setup Mode)

Jika Anda ingin mengganti koneksi WiFi atau alamat IP TV Box, gunakan **Mode Pengaturan (Captive Portal)**:

1.  **Masuk Mode Setup**: Matikan perangkat. Halangi **KEDUA** sensor IR (Kiri & Kanan) secara bersamaan dengan tangan, lalu nyalakan perangkat (Power On).
2.  **Indikator**: LED Tim A dan Tim B akan berkedip bergantian, dan LED Ready akan menyala terus (**Steady ON**).
3.  **Hubungkan WiFi**: Di smartphone/laptop, cari sinyal WiFi bernama `Paddle-Score-Setup`. Hubungkan tanpa password.
4.  **Konfigurasi**: Halaman pengaturan akan otomatis terbuka (atau buka browser dan akses IP `192.168.4.1`).
5.  **Simpan**: Masukkan SSID WiFi, Password, IP Android TV Box, dan Port (Default: 8888). Tekan **SAVE**. Perangkat akan restart otomatis.

---

## Indikator LED (Status Ready)

Gunakan LED pada pin **D8** untuk memantau status sistem:

- **Kedip Lambat (1:4)**: Sistem **Siap (READY)**. WiFi terhubung dan aplikasi TV Box terdeteksi. (Mode hemat energi/tidak mengganggu).
- **Kedip Cepat**: Sedang mencari WiFi atau aplikasi TV Box tidak ditemukan di jaringan.
- **Menyala Statis (Steady ON)**: Sedang dalam **Mode Setup (AP Mode)**.

---

## Cara Penggunaan (Operation)

Perangkat mendeteksi gerakan tangan di depan sensor IR:

### 1. Tim A (Kiri)
- **Gerakan Singkat (<0.5 detik)**: Menambah skor Tim A (+1).
- **Gerakan Lama (>1.5 detik)**: Mengurangi skor Tim A (-1).

### 2. Tim B (Kanan)
- **Gerakan Singkat (<0.5 detik)**: Menambah skor Tim B (+1).
- **Gerakan Lama (>1.5 detik)**: Mengurangi skor Tim B (-1).

### 3. Reset Pertandingan
- **Halangi Kedua Sensor (>1.5 detik)**: Mereset skor pertandingan ke 0-0.

---

## Troubleshooting
- **Tidak bisa connect WiFi**: Masuk ke Mode Setup dan pastikan Nama WiFi (SSID) dan Password benar (Perhatikan huruf besar/kecil).
- **Gagal mengirim skor**: Pastikan Android TV Box berada di jaringan WiFi yang sama dan aplikasi Padel Score sudah dalam posisi "Listening" pada port yang sesuai (Default 8888).
- **Sensor terlalu sensitif**: Putar potensiometer pada modul IR ke arah kiri (berlawanan jarum jam) untuk mengurangi jarak deteksi.
