# Readability refactor

The decompiled tree was still Zelix-KlassMaster-shaped: methods took a packed `Object[]` plus a fake `long` key,
every class carried unused reflection helpers, and almost every identifier was `a`, `b_float____`, `var12` or
`RenderHelper27`. This pass turned it into normal, readable Java without changing behaviour.

## What changed

**Structure** (done with a javac-driven tool, so every call site was resolved by the compiler, not by text matching):
- `m(Object[] a) { T x = (T) a[0]; long key = (Long) a[1]; ... }` called as `m(new Object[]{v, key})` became
  `m(T x)` called as `m(v)` — 1,488 method families, 7,262 call sites; constructors lost their trailing key too.
- The key plumbing (`long k2 = key ^ 0x...L;`, `var1 = 0L;`) is gone (~5,300 statements).
- Removed: ~1,500 unused per-class reflection helpers, 445 no-op string-table calls (`Event.b();`), 1,302 redundant
  casts (`(Float) floatSetting.getValue()`), unused locals/imports, and 30 dead classes — including the obfuscator's
  key-generator runtime that an earlier pass had misnamed `BufferBuilder`/`RenderHelper33`/`VertexSink`.
- Decompiler stack temporaries (`float var10002 = ...; f(var10002)`) were inlined back into expressions wherever the
  evaluation order provably stays the same; constant tables recovered by the previous pipeline were folded into literals.

**Names**: ~276 classes, ~2,400 fields, ~2,200 method families (overrides renamed together) and ~8,300 locals/parameters
now have descriptive names. Module setting fields are named after their UI display names, event classes after the game
hook that fires them, `@Subscribe` handlers `on<Event>`, hook handlers after the hooked method and injection point.
`docs/CODE_MAP.md` describes every class; `docs/rename_log.tsv` maps each old class/field/method name to its new one.
(`docs/classmap*.tsv`, `docs/mapping.tsv` and `docs/notes/` describe the tree *before* this refactor.)

**Runtime contracts updated alongside** (these bind by string, so the compiler cannot check them):
- `prestige_ui/manifest_live.json` — setting `field` names rewritten for all 686 settings.
- `ui/bridge/Bridge` — calls `Module.toggle()` without the old packed argument.
- `ui/mixin/EventBusStatsMixin` — targets `post(Event)` instead of the old `post(Object[])` descriptor.
- `ui/DevCmd` — uses compile-checked references instead of reflection on obfuscated names.
- `prestige-engine/transformers.list` — three empty `MinecraftClient` transformers removed, the remaining one renamed.

## Bug fixed: modules switching off when the menu closes
The client's keybind flag means **hold** (active only while the key is down, switched off whenever a screen opens), but
the previous pipeline had named it `toggleMode` and the ImGui menu was built on that name, so it showed the flag as
"Toggle" and set it when a key was bound. Every key bound in the menu became a hold bind; closing the menu with the menu
key (whose release briefly opens the legacy `ClickGuiScreen`) then switched those modules off. The flag is now
`KeybindSetting.holdMode` and `ui/bridge/Bridge` inverts it for the menu. The config format (`key.hold`) is unchanged;
binds saved while the bug existed show as "Hold" and can be switched back to "Toggle" in the menu.

## How it was verified
- Every pass recompiled the full tree with 0 errors; `gradlew build` succeeds.
- Each rename pass re-attributed the tree and compared all ~122,000 references: every one still resolves to the same
  declaration as before (no shadowing, overload capture or accidental overrides).
- A contract check loads the compiled classes and verifies every reflective name the UI bridge, `Bootstrap`, the manifest,
  the mixin configs, the transformer list and the Fabric entrypoints rely on.

## Known leftovers
- Raw types (`List`, `Set`, `Map`) and some casts on lambdas are decompiler output and were left as they are.
- A few names are best guesses where the original code is ambiguous or partly stripped (e.g. the empty `Native` stubs).
- `engine/mixin/I00..N41` keep numeric names on purpose: `EnginePlugin` generates those names by index.
