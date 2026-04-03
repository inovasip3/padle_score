# 🏓 Padel Scoreboard V2.1 - Setup Guide

## 📦 System Components

| Component | Purpose | Connection |
|-----------|---------|------------|
| **Android TV/Tablet** | Display device (Scoreboard View) | - |
| **Android APK V2.0** | Scoreboard app (Master Settings + Logic) | - |
| **ESP32-C3 (NEW)** | Laser TOF Controller (Pro Version) | **BLE HID** (Bluetooth) |
| **ESP8266 (Legacy)** | IR Sensor Controller | **WiFi** (HTTP) |

---

## 🔧 Part 1: Build & Install Android APK V2.0

### Prerequisites
- **JDK 17+**
- **Android SDK** (or Android Studio)

### Build
```powershell
cd android
.\gradlew.bat assembleDebug
```
APK path: `android/app/build/outputs/apk/debug/app-debug.apk`

### Setup as Kiosk
1. Install APK on Android TV Box/Tablet.
2. Set **Padel Scoreboard** as the default **Home Launcher**.
3. Grant necessary permissions (WiFi/Bluetooth).

---

## 🔌 Part 2: ESP32-C3 Laser Controller (BLE HID)

### Hardware Required
| Part | Qty | Notes |
|------|-----|-------|
| ESP32-C3 DevKit | 1 | Must support BLE |
| VL53L0X Laser TOF (GY-530) | 2 | One per side |
| Passive Buzzer | 1 | Feedback sound |
| LED | 1 | Visual indicator |

### Wiring (ESP32-C3)
| Signal | GPIO |
|--------|------|
| I2C SDA | 8 |
| I2C SCL | 9 |
| XSHUT Left Sensor | 4 |
| XSHUT Right Sensor | 5 |
| LED | 6 |
| Buzzer | 7 |

### Flash Firmware (PlatformIO)
1. Install **VS Code** + **PlatformIO** extension.
2. Open folder `esp32c3/`.
3. Connect ESP32-C3 via USB-C.
4. Click **Upload** (▶) in PlatformIO toolbar.

### Pairing BLE
1. Power up the ESP32-C3 (buzzer beeps 2x = ready).
2. On Android: **Settings → Bluetooth → Pair new device**.
3. Select **"Padel Remote V2"**.
4. Device now acts as a wireless keyboard — no app changes needed.

---

## 🎯 Part 3: Sensor Tuning Guide (VL53L0X Laser TOF)

> Bagian ini sangat penting untuk mendapatkan respons yang presisi,
> terutama saat menggunakan **gerakan raket padel** (bukan tangan biasa).

### Mengapa Raket Berbeda dari Tangan?

| Karakteristik | Tangan | Raket Padel |
|--------------|--------|-------------|
| Kecepatan dekati sensor | Lambat (~200–500ms) | **Cepat (~50–100ms)** |
| Permukaan refleksi | Tidak rata, menyerap sebagian | **Flat & reflektif — sinyal lebih kuat** |
| Durasi dalam zona | Bisa panjang | **Sangat singkat** |
| Risiko double-fire | Rendah | **Tinggi** (raket bisa "memantul" kembali) |
| Risiko false trigger | Rendah | **Ada** (angin ayunan bisa mengguncang sensor) |

---

### Cara Akses Serial Console untuk Adjustment

1. Colokkan ESP32-C3 ke laptop via kabel **USB-C**.
2. Buka **Serial Monitor** di VS Code / PlatformIO (atau aplikasi serial lainnya).
3. Set baud rate ke **`115200`**.
4. Ketik `INFO` + Enter → muncul semua parameter saat ini.
5. Kirim perintah penyesuaian sesuai tabel di bawah.
6. Nilai otomatis **tersimpan permanen** ke memori internal alat.

---

### Daftar Parameter & Perintah Serial

#### 🔴 `SET_DIST_IN <mm>` — Jarak Masuk Zona (Trigger In)
Jarak (dalam mm) agar raket/tangan **dianggap masuk** zona deteksi.

```
SET_DIST_IN 100
```

| Kondisi | Nilai Rekomendasi |
|---------|-----------------|
| Raket padel (tangan, flush) | `80` – `120` mm |
| Tangan biasa | `130` – `180` mm |
| Raket terlalu sensitif (mis-trigger) | Kurangi, coba `70` |
| Raket tidak terdeteksi | Naikkan, coba `130` |

> **Default: 100mm**

---

#### 🟠 `SET_DIST_OUT <mm>` — Jarak Keluar Zona (Hysteresis)
Jarak saat raket **dianggap sudah keluar** dari zona. Harus **lebih besar** dari `DIST_IN`.
Selisih antara `DIST_IN` dan `DIST_OUT` disebut **Hysteresis Band** — semakin lebar, semakin stabil dan tidak bergetar di batas.

```
SET_DIST_OUT 180
```

| Kondisi | Saran |
|---------|-------|
| Sering bouncing / getar di batas | Perlebar selisih: `DIST_IN + 80` |
| Respons lambat keluar zona | Perkecil selisih: `DIST_IN + 40` |
| Rekomendasi umum | `DIST_IN + 60` s/d `+ 80` mm |

> **Default: 180mm** (80mm lebih besar dari DIST_IN=100)

---

#### 🟡 `SET_DWELL <ms>` — Minimum Waktu Dalam Zona
Berapa lama (ms) raket **minimal harus berada** dalam zona agar dianggap gerakan disengaja — bukan noise atau angin lewat.

```
SET_DWELL 50
```

| Kondisi | Saran |
|---------|-------|
| Sering muncul poin yang tidak disengaja | Naikkan: `80` – `120` ms |
| Gerakan raket sangat cepat tapi sering miss | Turunkan: `30` – `40` ms |
| Rekomendasi raket padel | `40` – `80` ms |

> **Default: 50ms**

---

#### 🟢 `SET_CONFIRM <n>` — Jumlah Sampel Konfirmasi
Jumlah bacaan sensor berturut-turut yang harus konsisten di bawah `DIST_IN` sebelum trigger dikonfirmasi. Mencegah lonjakan noise sesaat (vibration/pantulan).

```
SET_CONFIRM 2
```

| Kondisi | Saran |
|---------|-------|
| Sering mis-trigger dari getaran | Naikkan: `3` – `4` |
| Gerakan sangat cepat & sering miss | Turunkan: `2` |
| Loop berjalan di 15ms → 2 sampel ≈ 30ms konfirmasi | Aman untuk raket |

> **Default: 2 sampel**

---

#### 🔵 `SET_COOLDOWN <ms>` — Jeda Wajib Setelah Aksi
Setelah aksi berhasil dikirim, sensor akan **diabaikan** selama durasi ini. Mencegah satu gerakan raket yang "memantul" atau melintas dua kali terhitung sebagai dua poin.

```
SET_COOLDOWN 700
```

| Kondisi | Saran |
|---------|-------|
| Satu gerakan raket sering mencetak 2 poin sekaligus | Naikkan: `900` – `1200` ms |
| Penilaian terasa lambat di permainan cepat | Turunkan: `500` – `600` ms |
| Rekomendasi umum | `600` – `900` ms |

> **Default: 700ms**

---

#### 🟣 `SET_DELAY <ms>` — Durasi Tahan untuk Aksi Minus (Long Press)
Berapa lama sensor harus **terus terblokir** (raket ditahan diam) agar aksi **Kurangi Poin** atau **Reset** terpicu.

```
SET_DELAY 1500
```

| Kondisi | Saran |
|---------|-------|
| Minus/Reset terlalu mudah terpicu | Naikkan: `2000` – `2500` ms |
| Minus/Reset terasa lambat | Turunkan: `1200` ms |
| Rekomendasi umum | `1500` – `2000` ms |

> **Default: 1500ms**

---

### Alur Lengkap Logika Deteksi Raket

```
Raket mendekati sensor
     │
     ▼
Apakah jarak < DIST_IN?
  Ya → Hitung confirmCount++
  Tidak → Reset confirmCount = 0, ulangi
     │
     ▼
Apakah confirmCount ≥ CONFIRM_SAMPLES?
  Ya → Trigger dikonfirmasi, catat waktu masuk (dwellStart)
     │
     ▼
Apakah jarak masih terjaga (< DIST_OUT)?
  Ya → Cek apakah sudah ≥ DELAY_LONG ms:
         Ya → Kirim LONG PRESS (Minus)
         Tidak → Tunggu...
  Tidak (keluar zona) → Hitung waktu dwell:
     │
     ├─ dwell < DWELL_MIN     → ❌ Abaikan (noise / angin / terlalu cepat)
     ├─ DWELL_MIN ≤ dwell < DELAY_LONG → ✅ Kirim SHORT PRESS (+ Poin)
     └─ dwell ≥ DELAY_LONG    → ✅ Kirim LONG PRESS (sudah dikirim tadi)
     │
     ▼
Aktifkan COOLDOWN (tidak bisa trigger ulang selama COOLDOWN ms)
```

---

### Tips Tuning Cepat di Lapangan

1. Gunakan `INFO` untuk melihat nilai saat ini.
2. Mulai dari default, lakukan 5–10 gerakan raket nyata.
3. Jika **sering double-poin** → naikkan `SET_COOLDOWN`.
4. Jika **sering miss** → turunkan `SET_DWELL` atau naikkan `SET_DIST_IN`.
5. Jika **sering false trigger** → naikkan `SET_CONFIRM` atau turunkan `SET_DIST_IN`.
6. Simpan semua perubahan secara otomatis — tidak perlu upload ulang firmware.

---

## ⚙️ Part 4: Master Settings Android App

**Akses:** Klik pada tulisan **"Padle Score v.2.0.0"** di pojok kanan bawah layar, lalu masukkan PIN (`1234`).
*(Catatan: Cara lama dengan tap 5x di background masih berfungsi sebagai cadangan).*

| Seksi | Isi |
|-------|-----|
| **Scoring Presets** | Standard Padel, American 11, American 21, atau Custom |
| **Visuals** | Warna tim, ukuran font, tipe font, efek menang |
| **Remote Control** | Toggle HTTP/WiFi, Toggle BLE HID, Key Bindings |
| **Advanced** | JSON Theme override untuk kustomisasi warna/font tingkat lanjut |

---

## 🛠️ Troubleshooting V2.1

| Issue | Solution |
|-------|----------|
| Poin tidak naik saat raket didekatkan | Cek BLE paired. Coba `SET_DIST_IN 130` |
| Poin naik 2x dari 1 gerakan raket | `SET_COOLDOWN 900` |
| Poin naik tidak disengaja (angin/getaran) | `SET_CONFIRM 3` atau `SET_DWELL 80` |
| Tombol Minus/Reset terpicu sendiri | `SET_DELAY 2000` |
| Buzzer berbunyi tapi poin tidak naik | BLE belum terhubung, pair ulang |
| Sensor tidak terdeteksi saat boot | Periksa kabel I2C, SDA=GPIO8, SCL=GPIO9 |
| BLE Remote tidak bisa dikontrol dari APK | Aktifkan "Enable BLE HID" di Master Settings |
| WiFi Remote lag | Upgrade ke ESP32-C3 BLE → latensi <10ms |
