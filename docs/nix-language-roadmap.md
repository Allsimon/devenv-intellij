# Nix language support: roadmap

The plugin owns the `.nix` file type (`AffogatoNix`). Its PSI comes from
affogato's `Tokenizer` and `TreeParser`; the IntelliJ side lives in
`com.allsimon.intellij.nixlang`.

## Done

- [x] File type, language, parser definition and PSI, with error recovery
- [x] Strings as injection hosts, with an escaper and a manipulator (manual
      "Inject language or reference")
- [x] Bash injected into `*Phase`, `pre*`/`post*` hooks, `shellHook`,
      `buildCommand`, `script`, `enterShell`, `enterTest` and `exec`; through
      `lib.mkBefore`/`mkAfter`/`mkForce`; and into the script of
      `runCommand*` and `writeShellScript*`
- [x] Syntax highlighting registered for the language
- [x] Lint and formatter skip injected fragments

## Cheap editor features

- [x] Commenter (`#` and `/* */`)
- [x] Brace matcher and quote handler
- [x] Code folding: attribute sets, `let` blocks, lists, multi-line strings
- [x] Structure view and breadcrumbs (attribute paths)
- [x] Highlighting from the PSI through an annotator: bindings, parameters,
      attribute names (selections included), builtins, interpolation braces
- [x] Color settings page (Settings | Editor | Color Scheme | Nix)
- [x] Spell checking in strings and comments (not in injected ones); TODO indexing
- [x] Lint: no duplicate syntax error where the parser already reports one
- [x] Lint: suppress a rule with `# affogato: ignore <rule>` (its line and the
      next) or `# affogato: ignore-file <rule>`, in affogato's linter, with
      "Suppress" quick fixes
- [x] Restartable highlighting lexer: an edit re-lexes from the nearest top-level token

## Injection

- [x] Other languages: by writer (`writers.writePython3`, `writeJS`, ...), by file
      name (`writeText "a.json"`, `builtins.toFile`, `environment.etc."a.json".text`),
      and Bash in `writeShellApplication`'s `text`
- [x] A `/* lua */` comment before a string, or `# language=json` above it, names its language
- [x] Settings for the attribute names and builders that get Bash (Languages & Frameworks | Nix)
- [x] IntelliLang support: "Inject language or reference" writes a `/* json */`
      comment before the string, and "Un-inject" removes it

## References

- [x] Resolve `let` bindings, lambda parameters, formals, `rec` sets and
      `inherit`; names that come from a `with` resolve softly; completion of the
      names in scope
- [x] Go to declaration, find usages, rename
- [x] File references in `./path`, `import ./x` and `callPackage ./x {}`,
      with completion and rename-on-move
- [x] Inspections for unused bindings and parameters: affogato's lint rules (`unused-let-binding`,
      `unused-lambda-arg`, `unused-formal`, `unused-at-pattern`), with fixes, shown greyed
- [x] Parameter info for lambdas with formals, including `callPackage ./pkg.nix { }`

## Intentions and templates

- [x] Convert between `"..."` and `''...''` (affogato's `Quotes`, keeping the value)
- [x] Expand `a.b.c = x;` into nested attribute sets, and collapse it back (affogato's `Bindings`)
- [x] Inline a `let` binding, where its value keeps its meaning at every use
- [x] Extract an expression into a `let` binding: the enclosing `let` where its variables keep
      their meaning, else a new `let` around the largest expression where they do
- [x] Wrap in `lib.mkIf`, the condition selected, and remove it again
- [x] Add a missing argument to `{ pkgs, lib, ... }:`, in the formals' layout
- [x] Live templates: `mkd` (`mkDerivation`), `mkopt`, `mken`, `mod` (a module with an
      enable option), `fgh` (`fetchFromGitHub`)
- [x] Postfix completion: `.mkIf`, `.list`, `.paren`, `.not`, `.toString`, in parentheses
      where the result is an operand

## Nix and devenv awareness

- [x] Completion and documentation for devenv options: from `devenv lsp` (nixd), which the
      plugin already starts
- [x] Completion of `pkgs.*` attributes and of `lib.*` with its documentation: from `devenv lsp`
      (nixd), against the nixpkgs pinned by `devenv.lock`
- [x] Gutter actions: `devenv up`/`test` at the top of `devenv.nix`, `processes.foo` to the
      Services view, `nix build`/`run`/`flake check` on a flake's `packages`, `apps` and `checks`
      (`devenv shell` and `nix develop` need a terminal, not a run console)
- [x] Run configurations: `devenv <arguments>` and `nix <arguments>` (`test`, `tasks run x`,
      `build .#x`, ...), in the devenv root unless set
- [x] Clickable `/nix/store/...-source/file.nix:line:col` in consoles, leading to the project's
      copy of the file; other absolute `.nix` paths too
- [x] Evaluate the selection, or the value at the caret, with `nix eval` in the file's directory,
      `pkgs` and `lib` bound when free; the result in a popup
- [x] Hashes: affogato's `fake-hash` rule marks placeholders; a `got:` hash in a console writes
      itself over the specified one, or the only placeholder; "Prefetch the hash" on `fetchurl` and
      `fetchFromGitHub`, in the background
- [x] Flakes: on `inputs.foo`, the gutter opens its `flake.lock` entry (with the locked revision)
      and runs `nix flake update foo`

## Language server in affogato

The IntelliJ plugin and the language server share their logic: what does not
need IntelliJ moves into affogato, over its own trees, and both call it.

- [x] Move scope resolution (definitions, references, names in scope) from
      `nixlang.NixScopes` into affogato (`Scopes` over a `SyntaxView`, with `SyntaxTree` for the
      server), and make the plugin use it
- [x] Language injection rules (which strings hold which language) in
      affogato (`Injections`), and the plugin's injector on them
- [x] An `affogato lsp` command over stdio (module `lsp`, no dependency): diagnostics from the
      linter, formatting, document symbols, go to definition, references, rename, completion,
      folding ranges
- [x] Semantic tokens over the LSP (from affogato's `Highlighter`), and the injections through a
      request of its own, `affogato/injections`
- [x] Code actions for the lint fixes and the suppression comments
- [x] Use it from the plugin where IntelliJ's LSP client fits better than PSI: nowhere for now.
      The plugin calls the code the server serves (lint, formatting, `Scopes`, `Injections`)
      directly, so running `affogato lsp` too would double the diagnostics, formatting and
      navigation; `devenv lsp` stays, for what takes evaluating Nix

## Coexisting with NixIDEA

- [ ] ~~Detect NixIDEA, which also claims `.nix`, and offer to disable it~~ (dropped)
