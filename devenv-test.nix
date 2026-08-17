{ lib, config, ... }:
let
  gradleVersion = config.languages.java.gradle.package.version;
  expectedDistributionUrl = "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip";
in
{

  # The committed wrapper stays the source of truth, but it must point at the
  # same Gradle as the shell and the packaged build.
  assertions = [
    {
      assertion = lib.hasInfix expectedDistributionUrl (
        builtins.readFile ./gradle/wrapper/gradle-wrapper.properties
      );
      message = ''
        gradle/wrapper/gradle-wrapper.properties does not match the devenv Gradle (${gradleVersion}).
        Expected it to contain:
          ${expectedDistributionUrl}
        Run 'gradle wrapper --gradle-version ${gradleVersion}' to update it.
      '';
    }
  ];
}
