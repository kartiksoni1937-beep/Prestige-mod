OpenPrestige Standalone Fabric Mod — Master Development Summary
================================================================

BUILD & ARTIFACTS
-----------------
- Clean Gradle build: ./gradlew clean build succeeds
- JAR: build/libs/prestige-0.1.0.jar (5.8 MB, 840 class files)
- Sources jar: build/libs/prestige-0.1.0-sources.jar (3.1 MB)
- fabric.mod.json present with:
  - id: prestige
  - name: OpenPrestige
  - version: 0.1.0
  - entrypoints: main (Prestige), client (Entry)
  - depends: fabric-loader >=0.19.0, minecraft ~1.21.11, java >=21, fabric-api *
- No imgui classes in the JAR (0 matches)
- No Overlay.java shipped (0 matches)
- No desktop input mixins (RenderSystemMixin, MouseHandlerMixin, KeyboardHandlerMixin) shipped (0 matches)
- Assets included: MSDF font (inter.json/inter.png), category icons, HUD icons, notification icons
- Mixin configs: prestige-engine.mixins.json (42 targets) + prestige.mixins.json (EventBusStatsMixin only)

GAME LOG — THE MOD IS SILENT BY DEFAULT
---------------------------------------
- Every diagnostic in the port is funnelled through dev.zprestige.prestige.util.Log.
- Log.OUT is false unless the JVM runs with -Dprestige.debug=true, so with normal PojavLauncher
  settings nothing from this mod reaches latest.log, stdout or stderr.
- No direct System.out / System.err / printStackTrace / slf4j logger remains outside Log.java.
- The only thread the mod starts (EnemyFaker skin lookup) catches its own throwables, so it cannot
  print an uncaught trace either.
- To debug on device, add -Dprestige.debug=true to the JVM arguments and the [Prestige] / [Engine] /
  [prestige-ui] lines come back.

OFFLINE VERIFICATION HARNESS — ALL CHECKS PASS
-----------------------------------------------
- 121 module classes load successfully from the JAR
- 686 settings fields verified with correct types against Bootstrap
- All 121 modules have valid no-arg constructors
- 42 ClassTransform game hooks resolve and transform against Minecraft 1.21.11 yarn mappings
- 0 transformer failures across all 42 targets
- MappedMeshBuilder.begin()/end() contract intact (chainable, no UnsupportedOperationException)
- ClickGUI layout: 11 pages, 121 modules, 11530 draw primitives generated across every page
  - Null-safety verified; text measures as zero width (MSDF font absent in harness, as expected)
  - Every widget draw path executes without exception

RENDERING ARCHITECTURE — REPLACED imgui BACKEND
-------------------------------------------------
- Removed imgui-java dependencies entirely
- Removed bundled desktop natives (no .so/.dll in JAR)
- Deleted: ui/mc/Overlay.java, ui/mixin/RenderSystemMixin.java, ui/mixin/MouseHandlerMixin.java,
          ui/mixin/KeyboardHandlerMixin.java, inject/hooks/*
- New native draw list: ui/NativeDrawList.java records ImGui-compatible primitives (rect, rounded rect,
  outline, line, circle, ring, quad, text, image) with per-command scissor guards; flush is a no-op
  when shaders are unavailable
- Rewritten backend: ui/Lib.java now replays draw primitives through the client's own shader pipeline
  (ShapeRenderer, DrawPipelines, RoundedTextureShader, RoundedOutlineShader) with a bundled MSDF font
  atlas (assets/prestige/font/inter.{json,png})
- Icons.java rewritten to use TextureLoader (no ImageIO/AWT dependencies)
- MsdfFont replaces all AWT/Swing rasterization
- CoreShaders: guarded loading + available(); flush no-ops when unavailable
- RoundedTextureShader.enqueueClipped(...) and RoundedOutlineShader.enqueueClipped(...) scissor overloads added

GUI — CLICKGUI PRESERVATION (Android-compatible Minecraft-native screen)
-------------------------------------------------------------------------
- New gui/ClickGuiScreen.java: Minecraft Screen subclass, renders in AFTER_HUD_RENDER callback
  flushed same-frame; responsive scale/origin; position persistence via config/prestige_ui.json
- Mouse + keyboard + scroll + char routing through Input class (no GLFW polling)
- Entry.java: no GLFW polling; desktop-only Skid splash skipped on Android via isAndroid()
- Theme persistence: ui config now saves "theme" index so the selected theme survives menu close
- Menu key (Ins/Hyper) toggle: fixed to close/reopen rather than reopen same screen, preventing double-toggle
- Touch-friendly controls, mouse support, keyboard support, responsive mobile layout, screen scaling
- Scrollable module lists, search functionality, toggles, sliders, dropdowns, category navigation
- Animations preserved via Lib easing functions (stagger, easeOutCubic)
- typography limitation documented: original DM Sans/DM Serif Display TTFs cannot be loaded without a TTF
  rasterizer; native GUI uses bundled MSDF Inter atlas. Visual effects that cannot be reproduced are
  documented rather than silently removed

INITIALIZATION — STANDALONE FABRIC
------------------------------------
- Prestige.init() marks EngineState.ready; all 42 engine hooks fire on startup
- AutoConfig.java: config persistence (<gameDir>/prestige/config.json), load at init, save on
  CLIENT_STOPPING
- EngineState.ready now includes open ClickGui (previously only set by GUI bridge, leaving hooks inert)
- Init failure now throws meaningful IllegalStateException
- Module registration via Native.java real registerModules using manifest injection from ui/bridge/Bootstrap
- No external injection processes required; no separate runtime injectors
- Bootstrap.java: null-safe settings, logs unsupported/failed setting reconstruction once, set() returns
  boolean; modsl() and loadModuleClass() made public
- Bridge.java: clearer bind/failure diagnostics; inject/Bootstrap: no silent catch in native probe/start
- SelfDestruct closes ClickGuiScreen via Input.active instead of deleted Overlay

MODULES ADDED AFTER THE PORT (Misc)
-----------------------------------
- Auto Sell — sends the configurable sell command (/sell by default), waits for the server window,
  shift-clicks the whole player inventory into it, closes it, and repeats every Interval (3000 ms).
  Settings: Command, Interval, Clicks (stacks per batch), Delay (ms between batches), Open Timeout,
  Close. A per-cycle cap stops it clicking a window that keeps bouncing items back.
- Auto Bone Block Filler — whenever a chest-type window is open, shift-clicks every bone block from
  the player inventory into it, and stops when the inventory has none left or the chest is full.
  Settings: Clicks (per batch), Delay (ms between batches, 50 = one batch per game tick).
- Auto Close — watches the open storage window (chest, barrel, ender chest, shulker box, hopper, dispenser,
  dropper) and closes it once every container slot holds an item, after Delay (150 ms default). Only the
  container's own slots are counted, so a half-empty chest never closes no matter how full the player
  inventory is; it never clicks or moves anything. Pairs with Auto Bone Block Filler: the chest shuts itself
  when full instead of staying open. Setting: Delay (ms).
- All three are registered in prestige_ui/manifest_live.json (registration + setting injection) and in
  prestige_ui/modules.tsv + settings.tsv (ClickGUI rows), so they appear in the Misc page like any
  original module and persist through the normal config save/load.
- Inventory clicks are paced per batch rather than per frame: the full 36-slot inventory moves in
  about 200 ms, which is imperceptible but avoids the packet flood that gets inventory modules
  kicked by server anti-cheat.

FRIENDS (Misc) — REWORKED, CENTRALIZED, PERSISTENT
--------------------------------------------------
- social/FriendManager.java is the single friends registry. Friends are keyed by UUID with the last known
  username stored for display, so a name change keeps the friendship. Name-only entries (added while the UUID
  was unknown) live in a separate set and are promoted to UUID form the first time that player is seen online.
- Persistence: <gamedir>/prestige/friends.json, written atomically on every add/remove and read lazily. It is the
  first real friend store, so there was no older format to migrate.
- Online tracking: FriendManager.tick() runs from ModuleManager.onClientTick (always, module on or off), rebuilds
  the online snapshot from the tab list, skips the local player, updates stored usernames and mirrors the result
  into the existing Socials page. Leaving a server clears the online snapshot; saved friends survive.
- GUI: the Socials page (existing native page 9) is the friends interface — All Players / Friends tabs, search,
  per-row "Add friend" / "Remove friend" buttons, avatar cards, and empty states. It was fully built but nothing
  ever fed it before this change; it is now wired to the manager through onAddFriend/onRemoveFriend, and its
  header shows "<n> online · <n> friends".
- Four independent settings: Green Friend Tags, Friend Protect, No Hit, Hitbox Protection (all on by default).
  * Green Friend Tags: NameTags colors friends green (85,255,85) instead of the usual cyan.
  * Friend Protect: FriendManager.isFriend(...) is identity AND this switch, so every existing targeting call
    site (Aim Assist, Kill Aura, Triggerbot, TargetTracker, TargetSelector, ...) honors it with no edits.
    It stays on while the module is disabled, which is exactly the pre-existing behaviour.
  * No Hit: the module cancels AttackEntityEvent / ResetAttackCooldownEvent / SwingHandEvent for friends. Every
    attack path (normal clicks and all combat modules) goes through ClientPlayerInteractionManagerMixin, so one
    hook covers them. Independent of Friend Protect.
  * Hitbox Protection: Hitboxes.onBoundingBox returns early for friends, so their box is never expanded; other
    players keep the configured expansion.

SAFE ANCHOR — FIXED LEGIT AND BLATANT PATHS
------------------------------------------
Legit (places a cover at the player's own crosshair):
- The aim test required the crosshair on the block directly under the anchor, whose only visible faces are
  its sides - effectively unhittable while charging the anchor, so the cover almost never ran. Any face whose
  placement lands in a free block touching the anchor is accepted now, the damage is simulated with and
  without that block, and the cover is only placed when it actually lowers the damage (and to <= Max Damage).
- Also checks the destination is empty of entities, so the module does not burn an anchor's single cover on a
  placement the server will refuse.
Blatant (places covers itself with silent rotations):
- Deadlock fixed: it only accepted a candidate once the limited silent rotation already pointed at the block
  and returned null otherwise, which means "do not rotate" - so the aim never converged and no cover was ever
  placed. A clamped rotation is now handed back so the manager keeps turning, and the cover is placed on the
  tick the ray actually reaches it.
- FOV is measured against the real target instead of the clamped step, so the setting bounds what it claims to.
- Cover candidates are the anchor's six faces (with the block below as the click target) instead of the old
  11x11 sweep, and the world scan is limited to reach, which removes the per-tick full-cube scan.
Own-anchor tracking (Only Own):
- Placements of respawn anchors made by this client are recorded by the always-registered block listener and
  cleared when the anchor disappears, instead of being collected only while Safe Anchor happened to be enabled.
  Enabling the module mid-fight, or rejoining, no longer leaves Only Own with an empty set and no covers.
- Legit mode adopts the nearest own anchor in reach when it has not seen a placement, so it also works when
  the anchor was placed before the module was enabled.

SOCIALS LIST — COMPACT SEARCH + DRAGGABLE SLIDER
-----------------------------------------------
- Search field shrunk from 250x35 to 200x30 and moved to the right end of the header row (right edge aligned
  with the page text at x=1000). Placeholder follows the tab: "Search friends" / "Search players". Same pill,
  icon, focus border and blinking caret as before; typing, backspace and Esc/Enter behave unchanged.
- Small vertical slider (6px track, right gutter at x=1076) over the list area whenever the filtered list is
  taller than the view. Draggable thumb (follows the cursor until release) and tap-to-jump on the track, which
  also gives touch users a way to scroll without a mouse wheel. Hidden entirely when everything fits.
- Fixed while wiring this: drawing skipped filtered rows but hover and the Add/Remove buttons still used the
  raw list index, so with a search active a click could add or remove a different friend than the visible card.
  Hover, drawing, clicking and scrolling now all use one filtered list (SocialsPage.visibleEntries()).
- clickYOffset is 0 now, so presses land exactly on what is drawn (it was 10px off, faithful to the original
  desktop version, which is wrong for touch).
- verifyClient covers the mechanics headlessly: filter count with a query, scroll clamps at the top and at the
  list height (which is what decides whether the slider exists), and a filtered list that fits cannot scroll.

REPAIRED SOURCE-CODE DEFECTS
-----------------------------
- MappedMeshBuilder.begin()/end(): Recovered from bytecode/contract — throw removed, begin→beginShape(),
  end→this with chainable return; evidence-based rationale documented
- Native.java: real registerModules, toggleMenu, isMenuOpen documented with one-time warnings for
  legacy no-ops; unused native methods audited and either removed or preserved with explanation
- Bootstrap.java: swallowed exceptions fixed; settings reconstruction now reliable with once-per-reconstruction
  logging; set() returns boolean; null-safe field access
- Bridge.java: failed module construction cannot silently leave module manager incomplete; clearer diagnostics
- Overlay.java: desktop rendering dependencies replaced; original GUI design preserved through NativeDrawList
  + MSDF shader pipeline
- ClickGuiScreen.java: full Android-compatible Minecraft-native implementation (see GUI section above)
- ShaderCompiler.java: shader compilation assumptions audited; guarded loading, no-unsupported-feature falls-backs

ANDROID ARM64 COMPATIBILITY STATUS
-------------------------------------
- Build compiles and produces a valid Fabric client JAR for Minecraft 1.21.11
- No x86-64-only libraries bundled; no Linux desktop natives in the JAR
- No JNI architecture mismatches; no Windows/macOS assumptions
- Graphics degrades gracefully: if MSDF font or shaders are unavailable, primitives are still generated
  (verified offline); visual effects that cannot be reproduced on Android are documented
- Xvfb + xkbshim shim verified for headless verification on Linux host (software OpenGL path)
- Actual Android/PojavLauncher device testing: unavailable during development; must be reported as pending
- Software Mesa (LIBGL_ALWAYS_SOFTWARE) used for headless verification; shaders operate in software
  mode but produce valid geometry
- typography limitation: original DM Sans/DM Serif Display TTFs replaced by bundled MSDF Inter atlas;
  this is a documented limitation, not a removal

REMAINING LIMITATIONS / PENDING ITEMS
--------------------------------------
1. Android runtime verification: Xvfb + software OpenGL works for headless checks, but actual PojavLauncher
   on Redmi 13C 5G (Dimensity 6100+, ARM64, Android 15) has not been tested. Touch input, GPU ES compatibility,
   and performance claims require a physical device or Android emulator with PojavLauncher.

2. GL4ES / OpenGL ES translation: The software Mesa path used for verification does not test GL4ES compatibility.
   When running on actual Android hardware, the mod will rely on PojavLauncher's OpenGL ES implementation.

3. Font rasterization: Original OpenPrestige used DM Sans/DM Serif TTFs with ImageIO/AWT. These cannot be loaded
   without a TTF rasterizer on Android. The replacement is a bundled MSDF Inter atlas (assets/prestige/font/).
   Visual fidelity for those specific TTFs is lost — documented as a limitation.

4. Xvfb xkbcomp path: The xkbcomp binary from the extracted Ubuntu root is invoked via an LD_PRELOAD shim
   that rewrites "/usr/bin/xkbcomp" calls to the correct path. This is a host-development workaround; it will
   not be present on the end-user's system. On Android, the keyboard init path is entirely different (PojavLauncher
   uses its own input stack).

5. Sound / Narrator: The Minecraft narrator failed to load library "flite" — a known optional feature, not critical.
   Sound engine initializes; no crashes observed.

6. Config file location: AutoConfig resolves the config dir via FabricLoader.getInstance().getConfigDir();
   on PojavLauncher this maps to the game's internal files directory. Verified working in headless mode.

7. Module 121 count preserved: All modules from the original manifest are present and loadable. No modules
   were removed or hardcoded.

CHANGE LOG (key entries)
------------------------
- Replaced imgui-java backend with NativeDrawList + MSDF font + shader pipeline
- Deleted Overlay.java and all desktop input mixins (RenderSystemMixin, MouseHandlerMixin, KeyboardHandlerMixin)
- Deleted inject/hooks/* and emptied inject-transformers.list
- Rewrote ClickGuiScreen.java as Minecraft-native Screen subclass with AFTER_HUD_RENDER callback
- Added AutoConfig.java for config persistence (prestige/config.json)
- Fixed Bootstrap.java: null-safe settings, once-per-reconstruction logging, set() returns boolean
- Fixed MappedMeshBuilder.begin()/end(): UnsupportedOperationException removed, contract recovered
- Fixed Native.java: real registerModules, documented legacy no-ops
- Added theme persistence to ClickGuiScreen config saving
- Fixed menu key toggle to close/reopen rather than reopen same screen
- Rewrote Icons.java to use TextureLoader (no ImageIO/AWT)
- Rewrote MsdfFont to replace all AWT/Swing rasterization
- Added CoreShaders guarded loading + scissor overloads
- Added RoundedTextureShader/RoundedOutlineShader with enqueueClipped scissor overloads
- Added EngineDev dev commands: menu, closemenu, guiinfo
- Removed imgui deps and version from build.gradle/gradle.properties
- 42 ClassTransform targets verified against Minecraft 1.21.11 yarn mappings
- 121 modules + 686 settings verified via offline harness
- Xvfb + xkbshim shim enables headless Linux verification
- fabric.mod.json updated: entrypoints, depends, mixins trimmed
- ClickGuiScreen: menu key + theme saved in prestige_ui.json