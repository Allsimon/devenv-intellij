# Nix editing

`.nix` files open in a Nix language of the plugin's own, parsed by
[affogato](https://github.com/Allsimon/affogato). It needs no devenv: any `.nix` file gets it, and affogato's
evaluator, running in the IDE, adds what takes evaluating Nix.

|                |                                           |
| -------------- | ----------------------------------------- |
| devenv options | (none)                                    |
| IDE settings   | Settings \| Languages & Frameworks \| Nix |
|                | Settings \| Editor \| Color Scheme \| Nix |

NixIDEA claims `.nix` too: with both plugins installed, the IDE asks which one opens them.

## Editing

- Highlighting of keywords, strings and their escapes, interpolations, paths, attribute names, parameters,
  builtins and constants, in colors the color scheme page sets.
- Brace matching, quote pairing, line and block comments, folding of sets, lists, `let` blocks, indented
  strings and block comments.
- The Structure tool window and the breadcrumbs show the bindings, nested as they are.
- Spell checking of comments and strings, `TODO` and `FIXME` in comments.

## Navigation

- Go to declaration, Find Usages and Rename for `let` bindings, `rec` sets, lambda parameters, formals and
  `inherit`. A name a `with` may bring stays unresolved, without an error.
- Completion of the names in scope, builtins and keywords, the attributes of a set the file binds after `s.`,
  and the names a `with` of such a set brings.
- Go to declaration on `s.a` and on a name a `with` brings leads to the attribute in the set, and on an
  attribute selected from `import ./file.nix` to its binding in that file.
- URLs in strings are links.
- Paths such as `./pkgs/hello` refer to their files and directories: they complete, and they follow a file
  that moves or is renamed.
- Parameter Info (Ctrl+P) inside `f { ... }` and `callPackage ./pkg.nix { ... }` lists the function's formals.

## Evaluation

affogato evaluates nixpkgs and the NixOS options in the IDE, in the background, the first time they are needed.
The status bar shows what is being evaluated. In a devenv project, `devenv.nix` and the modules it
imports get devenv's options, and the `pkgs`, `lib` and `inputs` devenv gives them, as `devenv lsp --print-config`
describes them; elsewhere, `pkgs` and `lib` come from `import <nixpkgs> { }`.

- Completion of `pkgs.*` and `lib.*`, of the names a `with pkgs;` brings, of NixOS, home-manager and devenv
  options in modules, of module arguments such as `inputs.*`, and of a flake's inputs and outputs, with each
  package's version.
- Quick Documentation and hover on a package, a `lib` function or an option: its version, description,
  homepage, type, default and example.
- Go to declaration on a package, a `lib` function or an option opens its definition in nixpkgs.
- Package versions are shown after `pkgs.hello`, as inlay hints.

Saving a `.nix` file evaluates again only what depends on it. The evaluation is dropped after ten minutes
unused, to give its memory back. Settings | Languages & Frameworks | Nix switches it off, and sets the nixpkgs
used outside devenv projects.

## Other languages in strings

Bash is injected into stdenv's phases and hooks, devenv's `enterShell`, `enterTest` and `exec`, the scripts
of `runCommand`, `writeShellScript` and `writeShellApplication`. The language of a `writers.*` function, or
of a file name - `writeText "config.json"`, `environment.etc."app.json".text` - is injected too, when the IDE
knows it. An interpolation becomes a placeholder, `$__nix` in Bash.

A comment names the language of any string: `/* lua */ ''...''`, or `# language=json` on the line above.
Inject Language or Reference writes such a comment, so the choice lives in the file.

Which attributes and builders get Bash is set under Settings | Languages & Frameworks | Nix.

## Refactorings

Alt+Enter offers, where they apply:

- Convert between `"..."` and `''...''`, keeping the value.
- Quote an attribute name, or unquote one that is a plain name.
- Expand `a.b = x;` into `a = { b = x; };`, and collapse it back.
- Inline a `let` binding, or extract the selection into one.
- Wrap a binding in `lib.mkIf`, or remove it.
- Add an undefined variable to the function's `{ ... }:` arguments.

Live templates: `mkd` (`stdenv.mkDerivation`), `mkopt`, `mken`, `mod` (a module with an enable option),
`fgh` (`fetchFromGitHub`). Postfix completion: `.mkIf`, `.list`, `.paren`, `.not`, `.toString`.

## Running

- Run icons: `devenv up` and `devenv test` at the top of `devenv.nix`; `nix build`, `nix run` or
  `nix flake check` on a flake's `packages`, `apps` and `checks`; `nix flake update` on its inputs, which also
  open their entry in `flake.lock`; a declared process opens in the Services tool window.
- The Nix run configuration runs `devenv` or `nix` with the arguments it is given, in the devenv root unless
  told otherwise.
- The positions Nix prints in a console, `/nix/store/...-source/pkgs/a.nix:3:5` included, lead to the
  project's file.
- Evaluate with nix eval, in the editor's context menu, shows the value of the selection, or of the binding at
  the caret; `pkgs` and `lib` are bound to `<nixpkgs>` when the code uses them without defining them.

## Hashes

- Placeholder hashes, `lib.fakeHash` and `sha256-AAAA...=`, are marked by the linter.
- When a build fails on a hash mismatch, the hash Nix got is a link in the console: it replaces the specified hash
  in the project, or its only placeholder.
- Alt+Enter on a `fetchurl` or `fetchFromGitHub` call offers Prefetch the hash: Nix fetches in the background, and
  the hash is written as `hash`.

## Lint

The [affogato](https://github.com/Allsimon/affogato) linter reports its findings, with their fixes. A comment
switches a rule off: `# affogato: ignore <rule>` for its line and the next, `# affogato: ignore-file <rule>`
for the file; Alt+Enter writes them.
