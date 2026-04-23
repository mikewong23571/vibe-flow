# Native Binary Spike

This spike checks how hard it is to turn the current `vibe-flow` CLI into a GraalVM native executable without changing the production install path.

## Current Assessment

Native binary integration is medium to high difficulty:

* The CLI needs an AOT-compatible Java main class.
* The build needs a reproducible jar or classpath input for `native-image`.
* Runtime behavior must be checked for Clojure dynamic loading, resource access, Java interop, shell execution, and file IO.
* Any future library that uses reflection or dynamic class loading may require native-image configuration.

The current codebase is a relatively good candidate because it mostly uses Clojure core, EDN, file IO, process execution, and simple Java interop.

## Local Result

Validated on this checkout:

* GraalVM `21.0.11-graal` was installed with SDKMAN.
* `native-image --version` succeeds.
* `clojure -T:build uber` succeeds.
* `java -jar target/vibe-flow-native-spike.jar help` succeeds.
* The generated uberjar is about 5.3 MB.
* One local timing sample: `clojure -M:cli help` took about 2.5s; `java -jar target/vibe-flow-native-spike.jar help` took about 0.8s.
* Bare `native-image --no-fallback -jar target/vibe-flow-native-spike.jar target/vibe-flow` builds a native executable, but the executable fails at startup because Clojure runtime resources are not fully available.
* Adding `--initialize-at-build-time=clojure -H:IncludeResources='clojure/.*'` builds a larger executable, but it still fails at startup because runtime loading reaches `clojure.lang.DynamicClassLoader#defineClass`, which native-image disallows unless classes are predefined during image build.
* Adding `com.github.clj-easy/graal-build-time` and building with `--features=clj_easy.graal_build_time.InitClojureClasses` produces a working native executable.
* The native executable is about 43 MB.
* One local timing sample: `./target/vibe-flow help` took about 0.01s.
* `./target/vibe-flow install-target --target <temp-git-repo>` succeeds against a git repo with an initial commit.
* `./target/vibe-flow bootstrap --target <temp-git-repo>` succeeds against a git repo with an initial commit.

Current native-image result: build succeeds and the basic user-facing CLI path works.

The working path depends on:

* `com.github.clj-easy/graal-build-time`
* `--features=clj_easy.graal_build_time.InitClojureClasses`
* direct linking during Clojure compilation
* avoiding Clojure reflection on Java interop in the CLI path
* shutting down Clojure agent threads in the native main wrapper

The spike-local `deps.edn` references `../../src` and `../../resources`; Clojure CLI warns that external `:paths` are deprecated. That is acceptable for this isolated spike, but a production build should move build configuration to the repository root or stage sources into a build directory.

## Files

* `deps.edn` defines the spike-local build aliases.
* `build.clj` builds an AOT uberjar with a native-image-friendly main class.
* `src/vibe_flow/native_main.clj` delegates to `vibe-flow.system/-main`.

## Build Uberjar

From this directory:

```bash
clojure -T:build uber
```

This writes:

```text
target/vibe-flow-native-spike.jar
```

Verify the jar:

```bash
java -jar target/vibe-flow-native-spike.jar help
```

## Build Native Image

Requires GraalVM with `native-image` installed and active on `PATH`.

```bash
native-image --no-fallback \
  --features=clj_easy.graal_build_time.InitClojureClasses \
  -jar target/vibe-flow-native-spike.jar \
  target/vibe-flow
```

Verify the binary:

```bash
./target/vibe-flow help
```

## Success Criteria

The spike is successful if:

* the uberjar builds reproducibly,
* `java -jar target/vibe-flow-native-spike.jar help` works,
* `native-image` can compile the jar with the Clojure build-time feature,
* the resulting binary can run `help`, `install-target` against a temporary git repo, and `bootstrap` against a temporary git repo.

If native-image fails, keep the failure output in this directory before promoting any production build work.
