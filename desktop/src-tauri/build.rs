fn main() {
    #[cfg(target_os = "macos")]
    {
        use std::process::Command;
        use std::path::Path;
        use std::fs;

        let swift_src = Path::new("swift/FocusBridge.swift");
        let bin_dir = Path::new("bin");
        let out_bin = bin_dir.join("focus_bridge");

        if swift_src.exists() {
            let _ = fs::create_dir_all(bin_dir);
            let status = Command::new("swiftc")
                .arg(swift_src)
                .arg("-O")
                .arg("-o")
                .arg(&out_bin)
                .status();

            if let Ok(s) = status {
                if s.success() {
                    println!("cargo:warning=Successfully built native macOS FocusBridge binary");
                }
            }
        }
        println!("cargo:rerun-if-changed=swift/FocusBridge.swift");
    }

    tauri_build::build()
}
