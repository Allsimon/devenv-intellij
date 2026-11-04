{
  pkgs,
  config,
  inputs,
  ...
}:
let
  affogatoMavenRepo = inputs.affogato.packages.${pkgs.stdenv.hostPlatform.system}.maven-repo;
in
{
  imports = [ ./devenv-test.nix ];

  languages.java = {
    enable = true;
    gradle.enable = true;
    gradle.package = pkgs.gradle_9;
    lsp.enable = false;
  };

  env.AFFOGATO_MAVEN_REPO = affogatoMavenRepo;

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
    inherit affogatoMavenRepo;
  };
}
