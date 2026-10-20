# OELib Scan Guard

A one-fix compatibility mod for Minecraft 1.20.1 (Forge).
It stops an **intermittent startup crash inside [OELib](https://github.com/Tower-of-Sighs/OELib)**
and does nothing else.

- **Minecraft**: 1.20.1 / **Loader**: Forge 47+
- **Requires**: OELib `[0.2.2, 0.3)`
- **License**: MIT
- **Remove this mod once upstream OELib fixes the race.**

## The crash

```text
Failed to create mod instance. ModID: oelib, class cc.sighs.oelib.forge.OELibForge
java.util.ConcurrentModificationException: null
  at java.util.LinkedHashMap$LinkedHashIterator.nextNode
  at java.util.AbstractCollection.addAll
  at java.util.HashSet.<init>
  at java.util.Set.copyOf
  at cc.sighs.oelib.network.api.NetworkAutoRegistration.findAllAnnotatedPackets
  at cc.sighs.oelib.forge.OELibForge.<init>
  ...
  at java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec   ← modloading-worker
```

It is a race, so it only happens some of the time. In a 289-mod pack we measured it at
**about 3% of starts** (6 of 207 recorded launches). Restarting "fixes" it, which is why it
tends to get written off as a flake.

## Why it happens

`NetworkAutoRegistration` holds its base packages in a synchronized wrapper:

```java
private static final Set<String> BASE_PACKAGES = Collections.synchronizedSet(new LinkedHashSet<>());
```

and snapshots them with:

```java
Set<String> packagesSnapshot = Set.copyOf(BASE_PACKAGES);
```

A synchronized wrapper only guards **single operations**. Its javadoc is explicit that the
*caller* must hold the wrapper's monitor while iterating — and `Set.copyOf` iterates.
Forge constructs mods **in parallel**, so any other mod calling `registerBasePackage`
(→ `BASE_PACKAGES.add`) at that instant mutates the set mid-iteration.

The more mods a pack has, the more likely the two overlap.

## The fix

Take the wrapper's own monitor around the copy. `Collections.synchronizedSet` uses the returned
wrapper as its mutex, so this is the very lock the mutating side already takes:

```java
synchronized (BASE_PACKAGES) {
    packagesSnapshot = Set.copyOf(BASE_PACKAGES);
}
```

This mod applies that via a Mixin redirect on `Set.copyOf` inside `findAllAnnotatedPackets`,
so **OELib's jar is not modified**. Both `copyOf` calls in the method are redirected (no `ordinal`),
which keeps it working if the call order changes; the second one copies a `ConcurrentHashMap`
key set, where the extra lock is uncontended and harmless.

## Upstream

The same code is present at OELib `HEAD` as of 2026-06-10, so this is not fixed by updating.
Reported upstream: **[Tower-of-Sighs/OELib#10](https://github.com/Tower-of-Sighs/OELib/issues/10)**.

Suggested upstream patch (3 lines, `common/.../NetworkAutoRegistration.java`):

```diff
-        Set<String> packagesSnapshot = Set.copyOf(BASE_PACKAGES);
+        Set<String> packagesSnapshot;
+        synchronized (BASE_PACKAGES) {
+            packagesSnapshot = Set.copyOf(BASE_PACKAGES);
+        }
```

## Building

```text
export JAVA_HOME=<jdk17>
./gradlew build
```

`libs/oelib.jar` is used at compile time only, to resolve the Mixin target. It is not bundled.

## 日本語

OELib の起動時クラッシュ（`ConcurrentModificationException`）だけを直す互換MODです。

OELib はパケット走査の対象パッケージ集合を `Collections.synchronizedSet` で持ちながら、
`Set.copyOf` で**ロックを取らずに反復**してコピーしています。同期ラッパーは単発の操作しか守らず、
反復時は呼び出し側がロックを取る必要があります（Javadoc に明記）。Forge は mod を**並列に構築**するため、
その最中に別のMODが `registerBasePackage` を呼ぶと集合が書き換わって落ちます。
レース条件なので**間欠**で、MOD数が多いパックほど当たりやすくなります。

このMODは Mixin でそのコピーをロックの中に入れるだけです。**OELib の jar は改変しません。**
作者が直したら、このMODは外してください。
