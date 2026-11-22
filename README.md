# File Manager Pro with AI Agent

**Open Source MT Manager Alternative with Gemini AI Integration**

A powerful Android File Manager with advanced APK editing capabilities and integrated AI coding assistant, built on top of the popular Amaze File Manager.

## Features

### File Management
- Complete file management operations (copy, move, delete, rename)
- Root explorer support for advanced users
- Support for multiple cloud storage providers
- FTP/SFTP server functionality
- Compressed file support (ZIP, RAR, 7z, TAR, etc.)
- Tab-based interface for multitasking

### APK Editor (MT Manager Style)
- **APK Decompilation**: Extract APK contents for editing
- **AndroidManifest.xml Editor**: Modify package name, permissions, components
- **Smali Code Editor**: Edit Dalvik bytecode directly
- **APK Signing**: Sign modified APKs with test keys or custom keystores
- **APK Information Viewer**: View detailed APK metadata
- **Security Analysis**: Analyze APKs for security vulnerabilities

### AI Assistant (Powered by Gemini)
- **Code Explanation**: Get explanations for smali code, manifest configurations
- **Error Analysis**: Understand and fix code errors with AI assistance
- **Code Modification**: Describe changes in natural language, AI applies them
- **Security Audit**: Deep security analysis of AndroidManifest.xml
- **Smali to Java Conversion**: Convert smali bytecode to readable Java code
- **Code Optimization**: Get suggestions for code improvements

## Screenshots

| Files View | APK Editor | AI Assistant |
|------------|------------|--------------|
| ![](screenshots/files.png) | ![](screenshots/apk_editor.png) | ![](screenshots/ai_assistant.png) |

## Building

Install JDK 17, Python 3, Rust via `rustup`, Android SDK Platform 34, Android Build Tools 34.0.0, and Android NDK r28c (`28.2.13676358`). Set `ANDROID_HOME` to your Android SDK directory and put `cargo` and `rustc` on your `PATH`.

### ARM64 debug APK

```bash
git clone https://github.com/justfukkinoff-oss/AI_File_Manager_Pro.git
cd AI_File_Manager_Pro
bash file_operations/setup_rust_android.sh --arm64
./gradlew :app:assembleFdroidDebug -Parm64Only=true
```

The signed debug APK is at `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`. The GitHub Actions Android Build CI workflow also builds it and uploads it as `android-arm64-debug-apk`.

For all four supported ABIs, omit `--arm64` from the Rust setup and omit `-Parm64Only=true` from Gradle. No Gemini API key is needed to build; the default build has AI requests disabled because its API key is empty.

## Project Structure

```
app/src/main/java/com/amaze/filemanager/
├── apkeditor/                    # APK editing functionality
│   ├── ApkEditorEngine.kt        # Main APK editing engine
│   ├── ApkInfo.kt               # APK metadata data classes
│   ├── ApkSigner.kt             # APK signing implementation
│   ├── ManifestEditor.kt        # AndroidManifest.xml editing
│   └── SmaliEditor.kt           # Smali code parsing/editing
├── ai/                          # AI integration
│   └── AiAssistantService.kt    # Gemini API integration
├── ui/fragments/apkeditor/      # UI for APK editing
│   ├── ApkViewerFragment.kt     # APK content viewer
│   ├── ApkFileEditorFragment.kt # File editor with AI
│   └── AiAssistantFragment.kt   # AI chat interface
└── ...
```

## Dependencies

### APK Editing
- **Android apksig**: APK signature creation and verification

### AI Integration
- **OkHttp**: HTTP client for Gemini API
- **Gson**: JSON serialization

### Core
- **Kotlin**: Primary language
- **AndroidX**: Modern Android libraries
- **Material Components**: UI components
- **RxJava**: Reactive programming

## Security Notes

### APK Signing
- Test keys are provided for development purposes only
- **Never distribute APKs signed with test keys in production**
- Use your own keystore for production releases

### AI Features
- API key should be set via `local.properties`
- Never commit API keys to version control
- All AI requests are sent securely via HTTPS

## License

```
Copyright (C) 2014-2024 Arpit Khurana, Vishal Nehra, Emmanuel Messulam, Raymond Lai and Contributors.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

## Acknowledgments

- **Amaze File Manager Team**: For the excellent base file manager
- **Google**: For the Gemini API
- **MT Manager**: For inspiration on APK editing features
- **All Contributors**: For making this project better

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting PRs.

## Version History

### v1.0-AI-Beta
- Initial release with APK editing capabilities
- AI Assistant integration with Gemini
- Security analysis features
- Smali code editing and analysis
