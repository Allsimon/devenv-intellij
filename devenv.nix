{ pkgs, ... }: {
  languages.java = {
    enable = true;
    gradle.enable = true;
    gradle.package = pkgs.gradle_9;
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
}
