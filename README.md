# PhotoTransfer - Android Photo Capture & Device-to-Device Transfer App

面向国际用户的 Android 照片拍摄与设备间传输应用。

## Features

- 📷 **Camera**: 使用 CameraX 拍摄照片，支持前后摄像头切换和闪光灯控制
- 🖼️ **Gallery**: 查看和选择本地照片
- 📡 **Transfer**: 通过 WiFi/蓝牙在两台设备间传输照片（使用 Nearby Connections API）
- 📊 **History**: 查看最近 10 条传输记录，支持重新发送失败的传输
- ⚙️ **Settings**: 应用设置和传输历史管理

## Technical Stack

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM with Clean Architecture
- **Dependency Injection**: Hilt
- **Database**: Room
- **Camera**: CameraX
- **Connectivity**: Nearby Connections API (Google Play Services)
- **Image Loading**: Coil
- **Navigation**: Navigation Component

## Key Features

### 双向传输模式
应用支持同时作为发送端和接收端，实现设备间的双向照片传输。

### 智能重试机制
传输失败时自动重试，最多重试 3 次。第 3 次失败后会标记为失败状态并记录到历史中。

### 传输历史管理
- 自动保留最近 10 条传输记录
- 超过 10 条时自动删除最旧的记录
- 支持手动清空全部历史
- 失败的传输可点击重新发送

## Project Structure

```
app/src/main/java/com/example/phototransfer/
├── data/
│   ├── local/
│   │   ├── entity/
│   │   │   └── TransferRecord.kt
│   │   ├── dao/
│   │   │   └── TransferRecordDao.kt
│   │   └── AppDatabase.kt
│   └── repository/
│       ├── PhotoRepository.kt
│       └── TransferRepository.kt
├── service/
│   ├── ConnectionManager.kt
│   ├── TransferManager.kt
│   └── TransferService.kt
├── ui/
│   ├── camera/
│   ├── gallery/
│   ├── transfer/
│   ├── history/
│   ├── settings/
│   └── MainActivity.kt
├── di/
│   └── AppModule.kt
└── PhotoTransferApplication.kt
```

## Permissions

应用需要以下权限：

- **相机**: `CAMERA`
- **存储**: `READ_MEDIA_IMAGES` (Android 13+) / `READ_EXTERNAL_STORAGE` (旧版本)
- **WiFi/蓝牙**: `BLUETOOTH_*`, `WIFI_*`, `NEARBY_WIFI_DEVICES`
- **位置**: `ACCESS_FINE_LOCATION` (设备发现所需)
- **前台服务**: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- **通知**: `POST_NOTIFICATIONS` (Android 13+)

## Build & Run

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Google Play Services (for Nearby Connections)

### Build

```bash
# Build the project
./gradlew build

# Install on device
./gradlew installDebug
```

## Usage

1. **拍照**: 打开应用后默认进入相机界面，点击拍照按钮即可拍摄照片
2. **选择照片**: 切换到 Gallery 页面，选择要发送的照片
3. **传输照片**:
   - 两台设备都打开 Transfer 页面
   - 点击 "Start Advertising" 和 "Start Discovering"
   - 等待设备连接
   - 连接成功后从 Gallery 选择照片并发送
4. **查看历史**: 在 Settings 中点击 "View Transfer History" 查看传输记录

## Notes

- 设备需要安装 Google Play Services 才能使用 Nearby Connections
- 首次使用需要授予相机、存储、位置、蓝牙等权限
- 传输时会显示前台服务通知
- 传输历史仅保留最近 10 条记录

## License

This project is for demonstration purposes.
