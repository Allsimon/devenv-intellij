{ pkgs, config, ... }:
{

  imports = [ ./devenv-test.nix ];

  languages.java = {
    enable = true;
    gradle.enable = true;
    gradle.package = pkgs.gradle_9;
    maven.enable = true;
    lsp.enable = false;
  };
  services.nginx.enable = true;

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
