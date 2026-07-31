{ pkgs, config, ... }: {

  languages.java = {
    enable = true;
    gradle.enable = true;
    gradle.package = pkgs.gradle_9;
    lsp.enable = false;
  };
  services.nginx.enable = true;

  # Keep the Gradle wrapper on the same Gradle as the shell and the packaged
  # build. 'copy' mode because the wrapper tasks rewrite this file in place,
  # while devenv stays the source of truth for its contents.
  files."gradle/wrapper/gradle-wrapper.properties" = {
    copyMode = "copy";
    text = ''
      distributionBase=GRADLE_USER_HOME
      distributionPath=wrapper/dists
      distributionUrl=https\://services.gradle.org/distributions/gradle-${config.languages.java.gradle.package.version}-bin.zip
      networkTimeout=10000
      retries=0
      retryBackOffMs=500
      validateDistributionUrl=true
      zipStoreBase=GRADLE_USER_HOME
      zipStorePath=wrapper/dists
    '';
  };

  treefmt = {
    enable = true;
    config.programs = {
      nixfmt.enable = true;
      oxfmt.enable = true;
    };
  };

  git-hooks.hooks = {
    treefmt.enable = true;
  };

  outputs.devenv-intellij = pkgs.callPackage ./package.nix {
    gradle = config.languages.java.gradle.package;
    jdk = config.languages.java.jdk.package;
  };
}
