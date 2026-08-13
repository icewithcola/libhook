{
  description = "Android development environment for libhook";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

  outputs = inputs:
    let
      javaVersion = 21;
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forEachSupportedSystem = f:
        inputs.nixpkgs.lib.genAttrs supportedSystems (system:
          f {
            pkgs = import inputs.nixpkgs {
              inherit system;
              overlays = [ inputs.self.overlays.default ];
              config.allowUnfree = true;
              config.android_sdk.accept_license = true;
            };
          });
    in
    {
      overlays.default = final: prev:
        let
          jdk = prev."jdk${toString javaVersion}";
          androidComposition = prev.androidenv.composeAndroidPackages {
            includeNDK = false;
            useGoogleAPIs = false;
            useGoogleTVAddOns = false;
            includeEmulator = false;
            includeSystemImages = false;
            includeSources = false;
          };
          androidSdk = androidComposition.androidsdk;
        in
        {
          inherit jdk androidSdk;
          gradle = prev.gradle.override { java = jdk; };
        };

      devShells = forEachSupportedSystem ({ pkgs }: {
        default = pkgs.mkShell {
          packages = with pkgs; [
            jdk
            androidSdk
            gradle
            gnupg
          ];

          JAVA_HOME = pkgs.jdk;
          ANDROID_HOME = "${pkgs.androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${pkgs.androidSdk}/libexec/android-sdk";
          # AGP's Maven-hosted AAPT2 is not executable on NixOS. Use the SDK binary, whose
          # interpreter is patched for the development shell.
          GRADLE_OPTS =
            "-Dorg.gradle.project.android.aapt2FromMavenOverride=${pkgs.androidSdk}/libexec/android-sdk/build-tools/36.0.0/aapt2";
        };
      });
    };
}
