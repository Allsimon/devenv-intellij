{
  lib,
  stdenv,
  runCommand,
  symlinkJoin,
  gradle,
  jdk,
  unzip,
}:

stdenv.mkDerivation (finalAttrs: {
  pname = "devenv-intellij";
  version = "1.0.0-SNAPSHOT";

  src = lib.fileset.toSource {
    root = ./.;
    fileset = lib.fileset.unions [
      ./build.gradle.kts
      ./settings.gradle.kts
      ./gradle.properties
      ./gradle
      ./src
      ./CHANGELOG.md
    ];
  };

  outputs = [
    "out"
    "dist"
  ];

  nativeBuildInputs = [
    gradle
    unzip
  ];

  # Regenerate deps.json with:
  #   $(nix-build --no-out-link -A mitmCache.updateScript)
  # then drop the entry that products-IU.json replaces:
  #   jq 'del(."https:/"."data.services.jetbrains")' deps.json | sponge deps.json
  mitmCache =
    let
      deps = gradle.fetchDeps {
        pkg = finalAttrs.finalPackage;
        data = ./deps.json;
      };

      # The plugin resolves the IDE download URL from JetBrains' product
      # listing. That response can't be locked by URL: the lockfile format
      # drops the query string it is requested with, and the listing changes
      # every time a build is published. Serve a pinned copy instead - the
      # caching proxy strips query strings, so any request for the listing
      # gets this file.
      productsListing = runCommand "jetbrains-products-listing" { } ''
        mkdir -p "$out/https/data.services.jetbrains.com"
        cp ${./products-IU.json} "$out/https/data.services.jetbrains.com/products"
      '';
    in
    symlinkJoin {
      name = "${finalAttrs.pname}-deps";
      paths = [
        deps
        productsListing
      ];
      passthru = { inherit (deps) updateScript; };
    };

  # The IntelliJ Platform Gradle Plugin resolves the target IDE through Java
  # toolchains, so point Gradle at the JDK we provide instead of letting the
  # foojay resolver download one.
  gradleFlags = [
    "-Dorg.gradle.java.installations.auto-download=false"
    "-Porg.gradle.java.installations.paths=${jdk}"
    # buildSearchableOptions starts the downloaded IDE, whose bundled JetBrains
    # Runtime is a prebuilt binary that can't run here.
    "-x"
    "buildSearchableOptions"
  ];

  gradleBuildTask = "buildPlugin";

  # The IntelliJ Platform test framework runs the tests on the IDE's bundled
  # JetBrains Runtime, which isn't executable on NixOS - run `gradle test` in
  # the devenv shell instead.
  doCheck = false;

  # The generic `nixDownloadDeps` task doesn't work on Gradle 9, and it wouldn't
  # pull in the IntelliJ Platform artifacts anyway - run the real task instead.
  gradleUpdateTask = "buildPlugin";

  installPhase = ''
    runHook preInstall

    install -Dm444 build/distributions/${finalAttrs.pname}-${finalAttrs.version}.zip \
      -t "$dist"

    # $out is the plugin directory itself, so it can be dropped into an IDE's
    # plugins/ directory (see jetbrains.plugins.addPlugins).
    unzip -q build/distributions/${finalAttrs.pname}-${finalAttrs.version}.zip -d unpacked
    mkdir -p "$out"
    cp -r unpacked/${finalAttrs.pname}/. "$out/"

    runHook postInstall
  '';

  meta = {
    description = "IntelliJ Platform plugin for devenv";
    # deps.json pins the Linux x86_64 IDE distribution.
    platforms = [ "x86_64-linux" ];
    sourceProvenance = with lib.sourceTypes; [
      fromSource
      binaryBytecode # dependencies fetched by Gradle
    ];
  };
})
