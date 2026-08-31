# Android Companion App - Build & Installation Guide

This guide provides step-by-step instructions for building and installing the **DND & Notification Syncer** Android app, whether you have never used Gradle before or prefer command-line workflows.

---

## 📋 Prerequisites & Requirements

- **Android Device**: Android 8.0 (Oreo / API Level 26) or higher.
- **Computer**: macOS, Windows, or Linux.
- **Wi-Fi Network**: Your phone and desktop must be connected to the same local Wi-Fi network (or LAN).

---

## 🛠️ Method 1: Build & Run with Android Studio (Recommended)

> **💡 Best for beginners**: Android Studio manages Java, the Android SDK, and Gradle automatically in the background—you do not need to install Gradle manually or run terminal commands.

### Step 1: Install Android Studio
1. Download and install **[Android Studio](https://developer.android.com/studio)** (version Koala 2024.1.1 or newer recommended).
2. During installation, ensure the **Android SDK** and **Android SDK Platform-Tools** options are selected (enabled by default).

### Step 2: Open the Project
1. Launch Android Studio.
2. Click **Open** (or **File > Open...**).
3. Navigate to the cloned repository and select the **`mobile-android`** directory:
   ```text
   dnd-syncer/mobile-android
   ```
4. Click **Open**.

### Step 3: Allow Gradle Sync to Finish
- When opened for the first time, Android Studio will automatically download the required Gradle distribution and Android SDK dependencies.
- Watch the progress bar in the bottom status bar until it says **"Gradle sync finished"** or **"Gradle build finished"**.
- *If prompted to install missing SDK platforms (e.g., API 35) or build tools, click the blue "Install missing platform(s)" link in the sync window.*

### Step 4: Prepare Your Android Device
1. **Enable Developer Options on your phone**:
   - Open **Settings** > **About Phone**.
   - Tap **Build Number** 7 times until you see the message *"You are now a developer!"*.
2. **Enable USB Debugging**:
   - Go to **Settings** > **System** (or **Additional Settings**) > **Developer Options**.
   - Turn on **USB Debugging**.
3. Connect your phone to your computer with a USB cable.
4. On your phone screen, accept the prompt: **"Allow USB debugging from this computer?"** (check *"Always allow from this computer"* and tap **Allow**).

### Step 5: Run the App
1. In Android Studio's top toolbar, ensure the **`app`** configuration is selected in the dropdown.
2. In the device dropdown next to it, select your connected Android device.
3. Click the green **Run (Play)** button (`Shift + F10` / `Ctrl + R`).
4. The app will compile, install, and automatically launch on your phone.

---

## 📦 How to Export a Standalone APK (Without Keeping Phone Plugged In)

If you want to create an installable `.apk` file that you can transfer to your phone or share with others:

1. In Android Studio, go to the top menu: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Wait for the build process to complete (usually 10–30 seconds).
3. A notification popup will appear in the bottom-right corner: **"APKs generated successfully"**.
4. Click the blue **"locate"** link inside the notification.
5. This opens the folder containing your APK:
   ```text
   mobile-android/app/build/outputs/apk/debug/app-debug.apk
   ```
6. Send `app-debug.apk` to your phone via USB cable, Google Drive, Quick Share / Nearby Share, or Email, then open and tap **Install**.

---

## 💻 Method 2: Command-Line (CLI) Build

If you prefer building from your terminal without Android Studio:

### Prerequisites:
1. **JDK 17**: Ensure Java 17 is installed (`java -version`).
   - macOS: `brew install openjdk@17`
   - Ubuntu/Debian: `sudo apt install openjdk-17-jdk`
   - Windows: Download JDK 17 from [Adoptium / Eclipse Temurin](https://adoptium.net/).
2. **Android SDK**: Install command-line tools and set the `ANDROID_HOME` or `ANDROID_SDK_ROOT` environment variable.

### Step-by-Step CLI Instructions:

1. **Navigate to the `mobile-android` directory**:
   ```bash
   cd mobile-android
   ```

2. **Configure Android SDK Location (if not set globally)**:
   Create a `local.properties` file in `mobile-android/`:
   - **macOS**:
     ```properties
     sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
     ```
   - **Linux**:
     ```properties
     sdk.dir=/home/YOUR_USERNAME/Android/Sdk
     ```
   - **Windows**:
     ```properties
     sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
     ```

3. **Build the Debug APK**:
   - **macOS / Linux**:
     ```bash
     chmod +x gradlew
     ./gradlew assembleDebug
     ```
   - **Windows**:
     ```cmd
     gradlew.bat assembleDebug
     ```

4. **Install to Connected Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📱 First-Time Setup on Your Phone

Once installed, launch **DND Syncer** on your phone and complete the permission wizard:

1. **Notification Access**:
   - Tap **Grant Notification Access**.
   - Find **DND Syncer** in the device list and toggle it **ON**.
   - *Why*: Allows the app to read incoming notifications and clear dismissed ones when you dismiss them from your desktop.
2. **Do Not Disturb Access (Notification Policy Access)**:
   - Tap **Grant DND Access**.
   - Find **DND Syncer** in the list and allow access.
   - *Why*: Allows the app to toggle Do Not Disturb mode when your desktop toggles Focus mode.
3. **Disable Battery Optimization**:
   - Tap **Ignore Battery Optimizations** and select **Allow**.
   - *Why*: Prevents Android from killing the background sync service when your screen is locked.
4. **Pair with Desktop**:
   - Make sure your desktop application is running on the same Wi-Fi.
   - Tap **Discover Desktops** or enter the 6-digit pairing PIN shown on your desktop screen.

---

## ❓ Troubleshooting & FAQs

### 1. `SDK location not found. Define location with an ANDROID_SDK_ROOT environment variable...`
- **Solution**: Open `mobile-android` in Android Studio once (it configures `local.properties` automatically), or manually create `mobile-android/local.properties` with `sdk.dir=/path/to/android/sdk` as shown in Method 2.

### 2. `JAVA_HOME is set to an invalid directory` or Java Version Incompatibility
- **Solution**: Android Gradle Plugin requires JDK 17+. In Android Studio, go to **Settings (Preferences on macOS) > Build, Execution, Deployment > Build Tools > Gradle** and ensure the **Gradle JDK** dropdown is set to **Embedded JDK 17** or **Version 17**.

### 3. `Permission denied: ./gradlew`
- **Solution**: Make the wrapper script executable:
  ```bash
  chmod +x gradlew
  ```

### 4. Device Not Showing in Android Studio or `adb devices`
- Ensure your phone is in **Transfer files / MTP** or **MIDI** mode rather than "Charge only".
- Reconnect the USB cable and watch for the **"Allow USB Debugging"** dialog on your phone screen.
- Verify ADB detects the device by running `adb devices` in terminal.

### 5. `Plugin relies on org.gradle.api.problems.internal.InternalProblems, a Gradle internal API that was removed in Gradle 9.6.0`
- **Why this happens**: Android Gradle Plugin (AGP 8.x) requires **Gradle 8.x** (such as Gradle 8.11.1). If you run a global/system Gradle 9.x, the build will fail with this internal API error.
- **Solution**: Use `mise` at the repository root (`mise install` sets Gradle 8.11.1), or use `./gradlew assembleDebug` which automatically uses the 8.11.1 distribution configured in `gradle-wrapper.properties`.

### 6. Desktop and Phone Cannot Find Each Other
- Ensure both devices are connected to the same Wi-Fi network.
- Check if your router has "Client Isolation" / "AP Isolation" enabled (disable it).
- Verify the desktop firewall allows incoming connections on UDP port `47891` and TCP port `47890`.
