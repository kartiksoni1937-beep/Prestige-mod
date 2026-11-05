# Code map

Every class of the client, grouped by package, with a one-line description.
Package paths are relative to `src/main/java/dev/zprestige/prestige/`.

## `(root)` — Core API: module/setting model, event bus, module manager (names here are used by the UI bridge via reflection).

- **BoolSetting** — Boolean (on/off) module setting.
- **Category** — ClickGUI category of a module (Combat, Misc, Movement, Visual, Mace, Spear, Menu).
- **ColorSetting** — Color (java.awt.Color) module setting.
- **EnumSetting** — Single-choice module setting whose value is one of a fixed list of string options.
- **EventBus** — Copy-on-write event bus: registers @Subscribe methods as MethodHandles and dispatches events by exact event class.
- **FloatSetting** — Float slider module setting with a min/max range.
- **Gamemode** — PvP game modes a module is tagged for (Crystal, Mace, Sword, Spear, UHC, Cart, Any); used by the UI gamemode filter.
- **IntSetting** — Integer slider module setting with a min/max range.
- **KeybindSetting** — Key/mouse-button bind setting (mouse buttons encoded as 9999 - button) with an optional hold/toggle mode.
- **ListenerEntry** — One registered event listener: the @Subscribe method, its owner object, the event type and a bound invoker handle.
- **MenuModule** — Pseudo-module holding the ClickGUI settings: menu key, description mode, rounded corners, accent colour.
- **Module** — Base class of every feature module: enabled/keybind core settings, metadata, toggling and event-bus (un)registration.
- **ModuleManager** — Holds the module list and menu module and runs onEnable/onDisable calls that were queued while no world was loaded.
- **MultiSetting** — Multi-select module setting: a list of string options each with its own on/off flag.
- **Native** — Empty stubs for the JNI functions of the removed native companion module (bound by @NativeId UUID): module registration, external menu toggling/query and opaque data calls.
- **RangeSetting** — Low/high range setting that can roll a random value inside the range (used for randomized delays).
- **Setting** — Base class of all module settings: name, value, description and a visibility predicate on the value.
- **SettingHolder** — Owns a list of settings and offers factory methods that create and register them (base of Module).
- **Subscribe** — Marks an event-handler method; EventBus registers @Subscribe methods by their single event parameter type.
- **TextSetting** — Free-text module setting with a placeholder hint.

## `client` — Mod entrypoint and the client-wide singletons.

- **Prestige** — Fabric mod entrypoint: creates the event bus, module manager and all client-wide singletons and hooks the HUD/world render callbacks.

## `module/*` — The modules, one package per ClickGUI category.

### `module/combat`

- **AimAssist** — Frame-based aim assist that smoothly pulls the camera yaw/pitch toward the best player or end-crystal target inside the FOV/range, with reaction delay, jitter, smoothing and movement prediction.
- **AnchorExploder** — Combat module that charges the respawn anchor under the crosshair with glowstone and detonates it from a chosen hotbar slot (optionally only your own anchors, with swap-back, enemy damage-tick timing and silent aim), and flags while it is looking at an anchor so vanilla item use is suppressed.
- **AnchorMacro** — Combat macro that places a respawn anchor at the crosshair while Place Bind is held, charges anchors with glowstone and explodes them from a chosen hotbar slot, each step with None/Swap/Silent swapping, and flags while it is acting so other anchor modules and item use stand down.
- **AnchorPearlCatch** — Combat module that predicts where an enemy's thrown ender pearl will land, pre-places a respawn anchor (optionally behind a safe cover block) beside the landing spot, charges it with glowstone and detonates it as the thrower arrives.
- **AnchorPlacer** — Combat module that places a respawn anchor where you aim (or silently aims at the nearest placeable spot), charges empty anchors with glowstone and detonates charged ones while holding a totem, in One-Shot or Persistent fire mode, flagging for two ticks after each action.
- **AntiAction** — Cancels accidental actions that hurt a PvP fight: double-charging anchors, opening ender chests with combat items, misplacing glowstone, ground fireworks, and sword hits into a raised shield.
- **AntiBot** — Flags server-side bots for BotFilter by position-packet validation counts, UUID version, missing tab-list entries and sustained zero ping.
- **AntiWeb** — Escapes cobwebs by placing a water bucket on top of the web column, waiting for the webs to wash away and scooping the water back up, either crosshair-triggered (Legit) or via silent rotations (Blatant).
- **AutoAnchor** — Fully automatic anchor-PvP module: picks the nearest enemy, scores candidate anchor spots by predicted target and self damage (with an optional glowstone/obsidian blast cover), then runs a place -> safe-cover -> charge -> detonate state machine with randomized stage delays, optional silent rotations, swap-back and fading box renders.
- **AutoCart** — TNT-minecart module: places a rail and TNT minecart at the crosshair or at the predicted landing of your bow/crossbow shot (on bow shot, on the trigger bind, or overload-stacked several carts), optionally drops a safe block, then ignites the cart with a bow/crossbow shot or auto-hits placed carts, using silent arc-aim rotations.
- **AutoCrystal** — Crystal-PvP module that breaks the end crystal under the crosshair (or, with Head Bob, a reachable crystal on the last base by adjusting pitch) and places crystals on the obsidian/bedrock you look at, with randomized delays, damage-tick gating and an optional last break before disabling.
- **AutoDrain** — Crystal-PvP module that drains water/lava sources near an enemy with an empty bucket (or covers them with cobweb as a fallback), either on the crosshair block (Legit) or via silent-aim rotations, then swaps back.
- **AutoElytra** — Combat module that swaps an elytra into the chest slot (inventory clicks or hotbar use), jumps and starts gliding with optional firework boosts, then swaps the chestplate and rockets back; also fires a rocket when Space is pressed mid-glide.
- **AutoHitCrystal** — Hit-crystal macro: places obsidian at the crosshair, switches to and places an end crystal on it, then breaks it (single tap, double tap or infinite), optionally synced to the enemy's damage ticks and using silent pitch changes.
- **AutoObbyCrystal** — Fully automatic obsidian-crystal module: picks the closest enemy, scores nearby crystal bases by predicted target/self damage, momentum and yaw change, then runs a PLACE_OBSIDIAN -> PLACE_CRYSTAL -> BREAK_CRYSTAL sequence with (silent) rotations, hotbar swapping, mid-sequence abort checks and fading box rendering.
- **AutoSafeCart** — Detects TNT minecarts that would deal enough damage to you (optionally only your own or only enemy carts) and places cover blocks (logs/planks/cobblestone/obsidian) next to you or the cart, either silently aimed or only at the crosshair in Legit mode.
- **AutoShield** — Sword/UHC module that holds the use key to block with a shield while a close enemy's swing cooldown is ready or they move towards you, lets go for your own falling crits, and tracks the ~5s axe shield-disable lockout (optionally swapping to a sword).
- **AutoShieldBreaker** — Sword module that swaps to an axe (hotbar, or silently pulled from the inventory) and hits a shield-blocking player to disable the shield, either when you click (On Hit) or automatically with optional silent aim, then swaps back.
- **AutoTotem** — Re-totems after a pop: Crystal mode double-hands a hotbar totem and runs an inventory routine that moves totems into the offhand/hotbar (optionally hover-only or with humanized robot mouse paths, dynamic danger-scaled delay and anti-TotemGuard checks); SMP mode swaps a totem into the offhand at low health or on a mace dive and swaps it back afterwards.
- **Backtrack** — Delays all incoming packets by a random latency while the attacked player moves away, tracking the target's real server position and rendering an interpolated box where it actually is.
- **CartAura** — Aura that hits nearby TNT minecarts placed by others: acquires a cart within the target FOV, tracks it with silent (optionally jittered) rotations, swaps to a sword and attacks after randomized reaction/attack delays, then swaps back.
- **CartPearlCatch** — Combat module that predicts an enemy pearl's landing spot, places a rail and TNT minecart within blast range of it, and detonates the cart with a flaming crossbow shot (through flint-and-steel fire) or a bow shot timed to the pearl's arrival.
- **CobwebPlacer** — Places cobwebs on enemies: Manual mode webs the block you look at, Auto mode predicts where the closest enemy will stand or land, aims there with silent rotations and places a web, pulling it from the hotbar or inventory and switching back afterwards via a deferred switch-back task.
- **DoubleAnchor** — Module that, when you right-click a charged anchor while holding another anchor, instantly re-places a second anchor in the blast spot (by chance, optionally charging it with glowstone after a delay), plus a Macro Bind that runs the full place/charge/explode+double-place/charge/explode sequence with damage-tick waiting and swap-back.
- **GhostPlace** — Crystal-PvP helper that, while you right-click an enemy, raycasts past them and places the held block (cobweb by default) on the block behind instead of interacting with the entity.
- **HitFix** — Holds back swings/attacks on players that are still in hurt time or airborne and lands the queued hit through a silent rotation once the target is hittable again (gives up after one second).
- **HitFlick** — Cancels each attack, flicks the silent rotation sideways by a random angle for one tick, then snaps back onto the target and lands the hit to obscure aim patterns.
- **Hitboxes** — Expands entity hitboxes, either directly through the bounding-box hook or (Grim/Vulcan mode) by turning air swings that would hit the expanded box into silent-rotation hits, and renders the boxes.
- **InstantCrossbow** — One-shot module that swaps to a crossbow (hotbar, or pulled from the inventory), holds use until it is charged, and restores the previous slot and disables itself once the crossbow leaves the hand.
- **InstantShield** — Keybind-driven module that swaps to a shield (hotbar, or pulled from the inventory) and holds block, swapping back on the next press (or on key release in hold mode).
- **KeepSprint** — After a sprint hit, releases the forward key and re-presses it after a random tick delay so the server-side sprint state is restored (stays out of the way while WTap is tapping).
- **KillAura** — Silent-rotation kill aura that locks the closest valid entity inside the target FOV, aims at the nearest enabled anatomy point of its hitbox and attacks when cooldown, reach and line of sight allow (optional axe swap against shields, miss chance and aim-dot rendering).
- **NoMissDelay** — Keeps the attack cooldown and hides the swing animation on missed sword/axe swings, with a humanize chance to behave like vanilla and an optional in-combat-only gate.
- **PearlFollow** — Combat module that simulates where an enemy's ender pearl will land and throws your own pearl at a solved pitch so it lands on the same spot, then swaps back to the previous hotbar slot.
- **PredictDoubleHand** — Crystal-PvP module that pre-emptively selects a hotbar totem when ThreatAssessor predicts lethal anchor/crystal/obsidian/mace damage from the closest enemy, on low-HP sword pops, or when throwing a pearl.
- **SafeAnchor** — Module that shields you from your own respawn-anchor blast by placing a glowstone/obsidian cover block between you and the anchor (Legit: the anchor you just placed, via your crosshair; Blatant: any nearby anchor via silent rotations), and reports when a cover is pending so the other anchor modules hold off detonating.
- **SilentAim** — Redirects missed sword/axe/mace swings (and block clicks) onto the closest nearby target through a silent rotation toward its nearest enabled aim point, optionally tracking continuously and rendering the aim dot.
- **SwapHit** — When attacking a player without a sword, swaps to a hotbar sword (or an axe against a raised shield), hits after the swap delay and swaps back after the swap-back delay.
- **TotemHit** — When hitting a player while holding a totem, re-issues the attack with a hotbar sword through a temporary slot swap.
- **Triggerbot** — Attacks the entity under the crosshair (or the ExtendedReach target) once the randomized delay and cooldown windows pass, with sticky targeting, crit timing, crystal rules, TPS-synced cooldown, miss chance and rate-limited debug logging of rejected targets.
- **XbowCart** — One-shot crossbow cart combo: places a rail and TNT minecart at the crosshair, lights fire with flint and steel on the arrow's path, then fires an arrow-loaded crossbow through the fire to detonate the cart and disables itself.

### `module/mace`

- **AutoMace** — Mace PvP assistant: swaps to the best Density/Breach mace once falling far enough, optionally auto-hits the target (trigger / silent aim), axe stun-slams shield-blocking targets before the mace hit, expands the target hitbox, swaps the elytra for a chestplate before impact and swaps back to the previous slot afterwards.
- **AutoPearlCatch** — Throws an ender pearl straight up and then a wind charge (forward-speed compensated) to catch it, either with a smoothed real-camera rotation (legit) or a silent server-side rotation lock, then disables itself.
- **AutoReMace** — After a mace hit on a player, re-enables AutoElytra and smoothly steers the camera above and then back onto the same target to chain another elytra mace dive.
- **BreachSwap** — On manual or automated attacks while holding a sword (and not falling), rolls a chance to swap to the highest-Breach mace for the hit and swaps back to the previous slot one tick later.
- **DiagonalPearlCatch** — Throws a pearl along the view direction and catches it with a wind charge, either after a fixed macro delay or by simulating the player, pearl and wind-charge trajectories to find the best intercept tick and angle, using a silent rotation lock for each throw.
- **ElytraWindCharge** — Equips and deploys the elytra, then throws a wind charge steeply downward whenever the glide gets close to the ground (threshold lowered by forward speed) to boost back up; disables if the player switches to a chestplate.
- **FastPearlCatch** — Pearl-and-wind-charge catch thrown back-to-back at configurable angles, either by swapping hotbar slots (silent lock or legit camera rotation) or by pre-loading the wind charge into the offhand and restoring the original offhand item and slot afterwards.
- **KeyWindCharge** — One-shot module that throws a wind charge when enabled, either by switching the hotbar with randomized delays and switching back (Normal) or via an instant silent slot swap (Silent), then disables itself.
- **MacePot** — Throws Speed and Strength splash potions downward (silent pitch) when those effects are missing, pulling potions from the main inventory into the hotbar when needed and swapping them back once both effects are active.
- **MacePrediction** — Renders the closest enemy's predicted airborne arc (with a landing marker) or on-ground movement path as boxes/outlines, plus range circles at the current or predicted landing position, to help time mace hits.
- **PearlWindCharge** — Throws an ender pearl and, as soon as the server teleports the player (pearl landed), throws a wind charge straight down at the feet (optionally jumping) and disables itself.
- **ShieldDrain** — Spams several attacks per tick on a shield-blocking player (crosshair target or nearest within horizontal range) to drain the shield, optionally auto-swapping to a mace and back; publishes a static per-tick draining flag that AutoMace checks.
- **ShortWindCharge** — Throws a wind charge at the player's feet, either through a staged legit sequence (switch slot, smoothly look down, throw, look back, switch back) or with a silent server-side pitch, optionally jumping as it is thrown.
- **WindChargeAura** — Silently rotates to and attacks other players' wind charges within reach to deflect them, with optional humanized aim jitter, a max-yaw cap and an in-air-only option.

### `module/misc`

- **AntiTrap** — Hides trap-bait entities (armor stands, minecarts, item frames, boats, crystals) near the player by dropping their spawn packets or discarding loaded ones, and blocks forced sign-edit screens.
- **AutoArmor** — Equips the highest-scoring armor piece (material tier plus weighted enchantments) from the inventory into each armor slot, gated by mode (always, inventory open, or held key) and a swap delay.
- **AutoClicker** — Left/right auto clicker: Interval mode clicks at a fixed rate, Ghost mode clicks at a randomized CPS with a Gaussian human reaction delay whenever the crosshair acquires a new entity.
- **AutoLoot** — While the inventory screen is open and the loot key is held, throws out surplus totems and ender pearls beyond the configured minimums to make room for looted items.
- **AutoPickaxe** — Switches to the best pickaxe (hotbar, or silently swapped in from the inventory) while the player mines the block beneath them, and switches back afterwards.
- **AutoPot** — Throws splash healing potions at the configured pitch, either automatically at low health or whenever a potion is held.
- **AutoText** — One-shot module that sends the configured chat message or slash command when enabled, then turns itself off.
- **AutoTool** — While the attack key is held on a block, selects the hotbar tool with the best mining speed plus Efficiency level and returns to the previous slot afterwards.
- **CartRefill** — Keeps a chosen hotbar slot stocked with a TNT minecart by swapping one in from the main inventory while the inventory is open or the refill key is held.
- **CartStealer** — Shift-clicks TNT minecarts out of an open shulker box at a randomized delay and optionally closes it when none are left or the inventory is full.
- **ChestStealer** — While the activate key is held, shift-clicks every item out of an open chest at a randomized delay and optionally closes it once empty.
- **Criticals** — Turns melee hits into critical hits, either by sending fake position packets (Silent) or by delaying the attack until after a real jump or micro-hop (Legit/Minimal).
- **CrystalOptimizer** — Removes an end crystal client-side as soon as it is attacked (unless the player has Weakness) instead of waiting for the server to confirm.
- **EnemyFaker** — Disguises the nearest non-friend player under a fake name, skin and tier/gamemode nametag, fetching the fake skin from Mojang on demand.
- **FastEXP** — Throws experience bottles quickly, either while right-click is held (Manual) or automatically swapping to bottles and looking down to mend damaged Mending gear (Automatic), as a silent rotation provider.
- **FastPlace** — Lowers the client's item-use cooldown to a random value in the configured range each tick so blocks (or any items) can be placed faster.
- **Friends** — Friend handling: highlights friend names in aqua in rendered text and blocks attacks, swings and attack-cooldown resets aimed at friends.
- **KeyPearl** — One-shot module that switches to an ender pearl in the hotbar, throws it and switches back to the previous slot with randomized delays, then disables itself.
- **MiddleClickFriend** — Adds or removes the player under the crosshair as a friend when the middle mouse button is clicked.
- **NameHider** — Replaces the local player's username in rendered text with a custom name or a periodically re-rolled random one.
- **PotionRefill** — While the inventory screen is open, shift-clicks splash potions (optionally healing only) from the main inventory into empty hotbar slots at a randomized delay.
- **Reach** — Randomly extends the player's attack reach by an amount from the configured range, with a configurable chance per hit.
- **Refill** — Tops off low stackable hotbar stacks with matching items from the main inventory while the inventory is open or the refill key is held.
- **RightClicker** — Schedules right clicks at a random CPS between the configured minimum and maximum while no screen is open and no item is being used.
- **SelfDestruct** — Panic switch that disables, disposes and unbinds every module, clears the module list and marks the client as self-destructed.
- **StreamerMode** — Masks every other player's name in nametags and rendered text with a stable numbered or random alias, optionally skipping friends and the local player.
- **Teams** — Tracks players whose display-name color matches the local player's as teammates so FriendManager treats them as friends (no attacking teammates).

### `module/movement`

- **AutoJumpReset** — Presses jump (by chance) on the tick you take a hit from a player while on ground, performing a jump reset to reduce knockback.
- **AutoShortPearl** — Anti-knockback module that, after you are hit by a player, waits a random delay and throws an ender pearl straight down at your feet using a silent ~90 degree pitch rotation.
- **FakeLag** — Holds outgoing packets in a queue and releases them manually, on a timer, by enemy distance, dynamically or after knockback (and on selected actions/explosions), drawing a ring at the last flushed position.
- **FastBridge** — Holds sneak automatically while looking down with a block in hand and standing over air, so you can bridge quickly at block edges.
- **Freecam** — Detaches the camera into a free-flying view driven by the movement keys while freezing the player's real position/rotation for the server, optionally drawing a box where the body stays.
- **NoJumpDelay** — Resets the player's jumping cooldown every tick to remove the delay between jumps.
- **Parkour** — Automatically jumps when the player is about to walk off a block edge (randomised edge distance).
- **ShortPearl** — One-shot module that throws an ender pearl at your feet at a steep pitch, either through a staged legit camera rotation (swap, rotate, throw, rotate back, switch back) or a silent server-side rotation.
- **Sprint** — Keeps the sprint key pressed while moving forward.
- **Velocity** — Scales incoming knockback by random vertical/horizontal percentages (with a chance), optionally skipping while you are still airborne from an explosion's knockback.
- **WTap** — After hitting a player while sprinting forward, waits a reaction delay and releases W for a random time to reset server-side sprint for extra knockback.

### `module/spear`

- **ExtendedReach** — Spear-mode module that, when attacking with a selected sword/axe/mace, briefly swaps to a non-Lunge spear for its longer reach and swaps back a tick later; also exposes the extended-reach raycast target to Triggerbot.
- **InstantLunge** — One-shot spear module that swaps to a Lunge-enchanted spear, fires the lunge (stab packet or piercing attack) immediately, and optionally swaps back after a random delay before disabling itself.
- **SpearDamageIndicator** — HUD overlay shown while charging a netherite spear that estimates the kinetic charge damage against the closest enemy and draws it as an animated text + bar panel below the crosshair.
- **SpearMaceDoubleTap** — Spear+mace combo module: when a KINETIC_ATTACK entity status (spear charge hit) arrives while falling farther than the trigger distance, stops the spear charge, swaps to the best Density/Breach mace and hits the crosshair target again on the next tick.

### `module/visual`

- **Ambience** — Visual module that overrides fog distances/color, the sky color and the client-side time of day.
- **Animations** — Visual module that replaces the first-person swing animation with one of three custom styles, can slow swings down and suppresses the item re-equip animation.
- **AntiResourcePack** — Module that cancels server resource-pack requests while replying that the pack was accepted and loaded.
- **ESP** — Shader ESP module: picks the outline/chams/aura post shader and the entity types to highlight for EspShaderManager, and holds the thread-local render layer captured by the model-render hook.
- **FreeLook** — Module that detaches the camera rotation from the player so you can look around (optionally in third person) without turning.
- **HealthIndicators** — Module that appends a color-coded health plus absorption value to player name tags.
- **HitEffect** — Visual module that spawns glowing physics particles when you hit an entity or a player pops a totem.
- **HudEditor** — Module that opens the HUD editor screen and holds the global HUD scale setting (percent).
- **ItemESP** — Item ESP module: collects dropped item entities within range every tick and draws "name xCount" labels above them on the 2D overlay.
- **JumpCircles** — Visual module that draws expanding glow rings where players jump.
- **NameTags** — Name Tags module: suppresses vanilla player labels and draws custom cards with name, totem pops, distance, a smoothed health bar, held/armor items and short enchantment labels.
- **NoBounce** — Module that cancels the hotbar item bobbing animation (e.g. while crystal modules swap items).
- **OreSimulation** — Ore Simulation module: re-runs vanilla ore and scattered-ore feature generation for loaded chunks from the entered world seed and outlines the predicted ore blocks around the player.
- **PearlTrajectory** — Pearl Trajectory module: simulates the flight of every thrown ender pearl, draws the predicted path in 3D and shows a landing tag with a pearl icon and the flight time at the predicted landing point.
- **RenderOptimizer** — Module that removes assorted visuals (hurt cam, fire overlay, particles, GUI background tint, weather, terrain loading screen, scoreboard, titles, hotbar item names) and can force full bright.
- **ShieldRenderer** — Module that publishes the shield overlay render settings (offset, scale, colors, mode) and tracks per-player shield blocking and break state.
- **StorageESP** — Storage ESP module: collects selected storage block entities (chests, shulkers, barrels, hoppers, furnaces, ...) every tick and renders them into a mask that StorageOutlineRenderer turns into colored glow outlines/fills.
- **TargetRenderer** — Visual module that highlights the current combat target with an orbiting ghost glow or a spinning square marker.
- **TotemAnimation** — Module that speeds up the totem-pop floating item animation by adding extra ticks.
- **Trails** — Visual module that leaves fading footstep, ring or particle trails behind players.
- **Trajectories** — Trajectories module: simulates the held bow, charged crossbow (including multishot spread) or ender pearl with dummy projectiles and renders the predicted path as an animated beam with an impact marker.
- **TwoDESP** — 2D ESP module: projects nearby players' bounding boxes to screen space and draws fading boxes/corners/brackets with optional glow, fill, animated gradient, health bar, name tag and distance label.
- **Weather** — Visual module that renders custom client-side rain, snow, firefly or ember particles around the player.
- **XRay** — XRay module: hides every block except a fixed list of ores, valuables and utility blocks via BlockVisibilityEvent, reloads chunk meshes on toggle and optionally forces full-bright lighting.

## `event` — Event types posted on the EventBus (fired from the hooks in `mixin/` through `util/HookBridge`).

- **AddParticleEvent** — Fired from ParticleManager.addParticle; cancelling drops the particle.
- **AntiBotQueryEvent** — Query posted by the bot filter; the AntiBot module cancels it to signal that bot filtering is active.
- **AttackEntityEvent** — Fired from ClientPlayerInteractionManager.attackEntity when the local player attacks; cancellable.
- **AutoAttackEvent** — Posted by combat modules right before they attack a player themselves (lets BreachSwap swap weapons first).
- **BlockStateUpdateEvent** — Fired after WorldChunk.setBlockState with the changed position and new block state.
- **BlockVisibilityEvent** — X-ray query fired from Block.shouldDrawSide, chunk occlusion and ambient-occlusion hooks: whether a block face should be drawn.
- **BotCheckEvent** — Query posted by the bot filter; AntiBot cancels it when the player looks like a bot.
- **BoundingBoxEvent** — Fired from Entity.getBoundingBox; when cancelled the returned box is replaced (used by Hitboxes).
- **CameraPositionEvent** — Fired from Camera.setPos; when cancelled the camera is moved to the event position instead (used by Freecam).
- **CameraRotationEvent** — Fired from Camera.setRotation; when cancelled the camera uses the event yaw/pitch (used by FreeLook).
- **ChunkDataEvent** — Fired after a chunk data packet has been applied, with the loaded chunk.
- **ClientTickEvent** — Main per-tick event, posted from MinecraftClient.tick (after the HUD tick) while a world and player exist.
- **DoAttackEvent** — Fired from MinecraftClient.doAttack (attack key); cancelling blocks the attack.
- **DoItemUseEvent** — Fired from MinecraftClient.doItemUse (use key); cancelling blocks the item use.
- **EntityAddRemoveEvent** — Fired when the client world adds (PRE phase) or removes (POST phase) an entity.
- **Event** — Base class of all client events: optional PRE/POST phase, a cancelled flag and post() to dispatch it on the event bus.
- **ExplosionEvent** — Fired from ServerWorld.createExplosion (integrated server) with the explosion power and position.
- **ExtendedReachAttackEvent** — Posted by Triggerbot before it clicks on a target found through ExtendedReach (lets the spear swap happen first).
- **FireOverlayEvent** — Fired while rendering the first-person fire overlay; cancelling hides it.
- **FloatingItemTickEvent** — Fired at GameRenderer.tick; the floating-item (totem) animation timer is advanced by the extra ticks set by listeners.
- **FogEvent** — Fired from FogRenderer.applyFog; when cancelled the fog color, start/end distances and sky end come from the event.
- **FrameEvent** — Posted once per rendered frame with the 2D render context (null context when no render pass ran); used for per-frame logic and notifications.
- **FrustumSetupEvent** — Fired after WorldRenderer.setupFrustum with the new view frustum (for visibility checks of 2D/3D overlays).
- **FullBrightEvent** — Fired from the lightmap update and EntityRenderer.getSkyLight; cancelling forces full brightness.
- **GameJoinEvent** — Fired after the client handles the GameJoin packet (joining a server or changing world); listeners reset per-world state.
- **HandSwingDurationEvent** — Fired from LivingEntity.getHandSwingDuration; when cancelled the event duration is returned (used by Animations).
- **HandleBlockBreakingEvent** — Fired from MinecraftClient.handleBlockBreaking; cancelling stops block breaking for that tick.
- **HealthIndicatorEvent** — Asks for a suffix (e.g. health) to append to a player's name label (used by HealthIndicators).
- **HurtCamEvent** — Fired from GameRenderer.tiltViewWhenHurt; cancelling removes the hurt camera shake.
- **ItemBobbingEvent** — Fired from ItemStack get/setBobbingAnimationTime; cancelling disables the item pickup bounce (used by NoBounce).
- **KeyEvent** — Fired from Keyboard.onKey with the GLFW key code and action; cancellable.
- **KeyboardInputTickEvent** — Fired PRE/POST around KeyboardInput.tick.
- **LabelTextEvent** — Lets modules rewrite the name label of a player (entity renderer and text-display hooks).
- **LoadingScreenEvent** — Cancellable event for skipping the downloading-terrain screen (RenderOptimizer); currently never posted.
- **ModuleStateSync** — Per-tick sync of module enabled state changed outside toggle() (e.g. by the UI), plus hiding the vanilla status-effect overlay while HUD elements are drawn.
- **MouseButtonEvent** — Fired from Mouse.onMouseButton with the button and action.
- **MouseUpdateEvent** — Fired from Mouse.updateMouse with the cursor delta; cancelling stops the vanilla camera turn (used by FreeLook).
- **MovementYawEvent** — Fired from Entity.updateVelocity and the sprint-jump boost of LivingEntity.jump; when cancelled movement uses the event yaw (silent rotation).
- **OverlayMessageEvent** — Fired from InGameHud.renderOverlayMessage; cancelling hides the action-bar/hotbar text.
- **PacketReceiveEvent** — Fired from ClientConnection.handlePacket for every incoming packet; cancelling drops it.
- **PacketSendEvent** — Fired from ClientConnection.send for every outgoing packet; cancelling drops it.
- **PlayerRenderStateEvent** — Fired PRE/POST around EntityRenderer.getAndUpdateRenderState for the local player so the server-side rotation can be shown on the model.
- **PreTickEvent** — Fired at the head of every MinecraftClient.tick, also without a world.
- **RainGradientEvent** — Fired from World.getRainGradient; cancelling removes rain and snow.
- **ReachEvent** — Fired from GameRenderer.updateCrosshairTarget; when cancelled the extra reach is added to the crosshair raycast distance.
- **Render2DEvent** — 2D HUD overlay render event, posted each frame while no screen (other than chat) is open.
- **Render3DEvent** — World-space render event posted from WorldRenderer.render with the world camera matrices.
- **RenderCrosshairEvent** — Fired from InGameHud.renderCrosshair; cancelling hides the vanilla crosshair.
- **RenderLabelEvent** — Fired before an entity name label is rendered; cancelling hides the vanilla label (used by NameTags).
- **RenderScoreboardEvent** — Fired from InGameHud.renderScoreboardSidebar; cancelling hides the scoreboard.
- **RenderStatusEffectsEvent** — Fired from InGameHud.renderStatusEffectOverlay; cancelling hides the vanilla status effect icons.
- **ResetAttackCooldownEvent** — Fired from PlayerEntity.resetTicksSince (attack cooldown reset); cancelling keeps the cooldown progress.
- **ResolutionChangedEvent** — Fired after MinecraftClient.onResolutionChanged with the game window.
- **ScreenBackgroundEvent** — Fired from Screen.renderBackground/renderBackgroundTexture; cancelling removes the dark GUI background tint.
- **SendMovementPacketsEvent** — Fired PRE/POST around ClientPlayerEntity.sendMovementPackets; PRE may change the rotation sent to the server, the real rotation is restored after POST.
- **ShieldBreakEvent** — Fired from LivingEntity.handleStatus when an entity's shield is broken (status 30).
- **ShieldRenderSettingsEvent** — Query event that the ShieldRenderer module fills with its overlay settings (offset, scale, mode, colors).
- **SkinTexturesEvent** — Fired when a player's skin textures are looked up (player list entry / client player); when cancelled the event skin is used.
- **SkyColorEvent** — Fired from SkyRendering.updateRenderState so modules can override the sky and sunrise/sunset colors.
- **SwingArmEvent** — Fired from HeldItemRenderer.swingArm so swing animations can be replaced (used by Animations).
- **SwingHandEvent** — Fired (PRE, then POST) from ClientPlayerEntity.swingHand; cancelling suppresses the swing, skipPost suppresses the POST phase.
- **TextReplaceEvent** — Fired for every visited text string; when cancelled occurrences of the target are replaced with the event text (name hiding).
- **TimeOfDayEvent** — Fired from World.getTimeOfDay; when cancelled the event time is returned (used by Ambience).
- **TravelEvent** — Fired PRE/POST around LivingEntity.travel for the local player so movement uses the silent rotation; the real rotation is restored afterwards.
- **VelocityUpdateEvent** — Fired from Entity.setVelocityClient for the local player (server knockback); cancelling applies the modified velocity.
- **WorldRendererReloadEvent** — Fired at the start of WorldRenderer.reload.

## `mixin` — ClassTransform hook classes injected into Minecraft (applied by `engine/EnginePlugin`).

- **AbstractBlockStateMixin** — ClassTransform hook into AbstractBlockState.getAmbientOcclusionLightLevel that lets the ambient-occlusion visibility event force full AO light (XRay).
- **AbstractClientPlayerEntityMixin** — ClassTransform hook into AbstractClientPlayerEntity.getSkin that lets SkinTexturesEvent replace a player's skin textures.
- **BlockMixin** — ClassTransform hook into Block.shouldDrawSide that lets the draw-side visibility event override face culling (XRay).
- **CameraMixin** — ClassTransform hook into Camera.setPos/setRotation that lets camera position and rotation events override the camera transform.
- **ChunkOcclusionDataBuilderMixin** — ClassTransform hook into ChunkOcclusionDataBuilder.markClosed that can skip occlusion marking so hidden blocks keep rendering (XRay).
- **ClientConnectionMixin** — ClassTransform hook into ClientConnection that posts cancellable packet receive/send events and feeds outgoing packets to the inventory swap queue.
- **ClientPlayNetworkHandlerMixin** — ClassTransform hook into ClientPlayNetworkHandler that posts GameJoinEvent and ChunkDataEvent after the vanilla packet handlers.
- **ClientPlayerEntityMixin** — ClassTransform hook into ClientPlayerEntity that posts swing-hand events and spoofs, then restores, the rotation sent in movement packets.
- **ClientPlayerInteractionManagerMixin** — ClassTransform hook into ClientPlayerInteractionManager.attackEntity that posts a cancellable AttackEntityEvent for the local player.
- **ClientWorldMixin** — ClassTransform hook into ClientWorld that posts entity added and removed events.
- **EntityMixin** — ClassTransform hook into Entity that applies silent-rotation movement and aim fixes and lets events override bounding boxes and client-set velocity.
- **EntityRendererMixin** — ClassTransform hook into EntityRenderer that applies the visual rotation to the local player's render state, forces full entity sky light and rewrites name labels.
- **FogRendererMixin** — ClassTransform hook into FogRenderer that lets FogEvent rewrite the fog uniform buffer and the returned fog color.
- **GameRendererMixin** — ClassTransform hook into GameRenderer that runs the client's HUD render pass, captures camera matrices, cancels hurt cam, speeds up the floating totem item and extends reach in crosshair targeting.
- **HeightContextMixin** — ClassTransform hook into HeightContext's constructor that accepts a null chunk generator (client-side ore simulation) by using unbounded height limits.
- **HeldItemRendererMixin** — ClassTransform hook into HeldItemRenderer.swingArm that posts SwingArmEvent so modules can replace the first-person swing animation.
- **InGameHudMixin** — ClassTransform hook into InGameHud that lets events hide the status effect overlay, crosshair, scoreboard sidebar and overlay message.
- **InGameOverlayRendererMixin** — ClassTransform hook into InGameOverlayRenderer.renderFireOverlay that lets FireOverlayEvent hide the fire overlay.
- **InactivityFpsLimiterMixin** — ClassTransform hook into InactivityFpsLimiter.update that keeps the full FPS limit while the ClickGUI is open.
- **ItemStackMixin** — ClassTransform hook into ItemStack that lets the item bobbing events suppress the hotbar bobbing animation time.
- **KeyboardInputMixin** — ClassTransform hook into KeyboardInput.tick that posts input tick pre/post events and blocks movement input (then restores sprint) around inventory swaps.
- **KeyboardMixin** — ClassTransform hook into Keyboard.onKey that posts a cancellable KeyEvent.
- **LightmapTextureManagerMixin** — ClassTransform hook into LightmapTextureManager.update that clears the lightmap to white for full bright.
- **LivingEntityMixin** — ClassTransform hook into LivingEntity that spoofs the rotation during travel, overrides hand swing duration, posts shield-break events and applies the silent jump yaw.
- **LivingEntityRendererMixin** — ClassTransform hook into LivingEntityRenderer.isVisible that forces invisible ESP-targeted entities to render.
- **MinecraftClientMixin** — ClassTransform hook into MinecraftClient that posts client tick, pre-tick, resolution change, item use, attack and block breaking events.
- **ModelCommandRendererMixin** — ClassTransform hook into ModelCommandRenderer that tracks the render layer being drawn and wraps model vertex consumers with the ESP outline consumer.
- **MouseMixin** — ClassTransform hook into Mouse that posts a cancellable mouse move event and mouse button events.
- **NamespaceResourceManagerMixin** — ClassTransform hook into NamespaceResourceManager.getResource that serves resources in the prestige namespace from the client jar.
- **ParticleManagerMixin** — ClassTransform hook into ParticleManager.addParticle that posts a cancellable AddParticleEvent.
- **PlayerEntityMixin** — ClassTransform hook into PlayerEntity.resetTicksSince that posts a cancellable attack-cooldown reset event.
- **PlayerListEntryMixin** — ClassTransform hook into PlayerListEntry.getSkinTextures that lets SkinTexturesEvent replace tab-list skins.
- **PostEffectProcessorMixin** — Empty transformer for PostEffectProcessor (its hook bodies were not recovered); kept because the engine checks the hooked-class count against prestige-engine/targets.*.list.
- **ReloadableResourceManagerImplMixin** — ClassTransform hook into ReloadableResourceManagerImpl.getResource that serves prestige resources and the client's own glow, blur and rounded-rect shader files.
- **ScreenMixin** — ClassTransform hook into Screen that lets ScreenBackgroundEvent hide the menu background and background texture.
- **ServerWorldMixin** — ClassTransform hook into ServerWorld.createExplosion that posts an ExplosionEvent (integrated server).
- **SkyRenderingMixin** — ClassTransform hook into SkyRendering.updateRenderState that lets SkyColorEvent override the sky and horizon colors.
- **TextDisplayEntityRendererMixin** — ClassTransform hook into TextDisplayEntityRenderer.getLines that lets the label text event rewrite text-display lines naming a player.
- **TextVisitFactoryMixin** — ClassTransform hook into TextVisitFactory.visitFormatted that applies TextReplaceEvent replacements to rendered text.
- **WorldChunkMixin** — ClassTransform hook into WorldChunk.setBlockState that posts a BlockStateUpdateEvent for client-side block changes.
- **WorldMixin** — ClassTransform hook into World that lets events override the rain gradient and the time of day.
- **WorldRendererMixin** — ClassTransform hook into WorldRenderer that runs the client's 3D render pass after the frame graph and posts frustum setup and renderer reload events.

## `util` — Hook bridge between the injected game hooks and the client, keybinds, simulated clicks, notifications.

- **FrameDeltaTimer** — Measures the time between rendered frames (capped at 30 ms) and exposes it as an animation delta factor used for smooth rotations.
- **HookBridge** — Static trampolines called by the ClassTransform hook classes in mixin/: each creates, queries or posts one client event for a hooked Minecraft method, plus resource loading for prestige assets/shaders and the GUI/world render-pass plumbing.
- **LruCache** — Access-ordered LinkedHashMap that evicts its eldest entry once it holds more than maxSize entries.
- **NotificationManager** — Queues toast notifications (title, message, duration, type) and renders them stacked in the bottom-right corner every frame, dismissing duplicates.
- **SimulatedClickManager** — Tracks simulated mouse-button presses (button -> remaining hold ticks), releases them when they expire or the window loses focus, and suppresses vanilla attack/use/block-breaking while a simulated click is held.

## `combat` — Targeting, rotations, damage and threat calculation.

- **BotFilter** — AntiBot backend: keeps the set of position-packet-validated players and answers (cached per tick) whether an entity is a valid, non-bot target.
- **ClickScheduler** — Schedules a simulated mouse-button click after a delay and releases it after a randomized hold time (used by AutoClicker/RightClicker).
- **EquipmentSlotSwitchMap** — Compiler-generated enum switch map translating EquipmentSlot ordinals to AutoArmor switch cases.
- **ExplosionDamageCalculator** — Computes end-crystal, respawn-anchor and TNT-minecart explosion damage to an entity (exposure raycasts, armor, protection, resistance, difficulty) with a per-tick cache.
- **HudStyle** — Shared HUD style constants (text scales, paddings, corner radius, palette) and row/card height helpers used by HUD elements and nametags.
- **ItemUseCooldownAccessor** — Reflective accessor that reads and writes MinecraftClient.itemUseCooldown (used by FastPlace).
- **JumpCooldownAccessor** — Reflective accessor that writes LivingEntity.jumpingCooldown (used by NoJumpDelay).
- **NameTagEntry** — Per-player snapshot rendered by the NameTags module (name, pop count, health ratios and texts, measured widths, equipment and enchantment labels).
- **PearlFollowSimulatedPearl** — Client-side simulated ender pearl used by PearlFollow to predict where a pearl lands; stops and snaps to the hit position on collision.
- **PearlTrajectorySimulatedPearl** — Client-side simulated ender pearl stepped by the PearlTrajectory module to trace a thrown pearl's flight path.
- **PotionDisplayEntry** — Per-status-effect row state of the Potion Display HUD element (name, level, color, durations and fade animation).
- **TargetSelector** — Static target-selection helpers: closest enemy player/entity (optionally within an aim angle), player detection and shield-blocking checks.
- **TargetTracker** — Remembers the enemy player the local player last attacked (directly or via a nearby crystal) and forgets it after 5 s, death or 15 blocks.
- **ThreatAssessor** — Finds the most dangerous incoming damage source around an enemy (crystals, charged anchors, obsidian crystal spots, mace smashes) for totem prediction.
- **TrajectoriesSimulatedPearl** — Client-side simulated ender pearl stepped by the Trajectories module to preview the local player's pearl throw.
- **TwoDEspTarget** — Per-tick snapshot of a player shown by the 2D ESP module (name, health, size, distance, friend flag).
- **VisualRotationManager** — Smoothly interpolates the server-side rotation and temporarily applies it to the local player model while its render state is built, restoring the real rotation afterwards.

## `inventory` — Hotbar/inventory search and swapping.

- **AnchorUseGuard** — Decides whether vanilla item use should be cancelled while an anchor module is active and the player holds or aims at a respawn anchor.
- **DeathLootDetector** — Detects valuable PvP loot (gear, totems, gapples, crystals...) dropped near a position, i.e. a fresh kill, for the Pause On Kill options.
- **EnchantmentLabel** — Short enchantment label (e.g. "pro 4") drawn above an item in the NameTags module.
- **HeldItemRendererAccessor** — Reflective accessor for HeldItemRenderer equip progress and held stacks (used by the Animations module).
- **InventoryUtil** — Static inventory and hotbar helpers: weapon/item type checks, slot clicks and swaps, hotbar slot search/selection and splash potion lookup.
- **ItemEspLabel** — Label text and stack of a dropped item rendered by the ItemESP module.
- **PlayerInteractions** — Static helpers that perform player interactions (use block/item, attack, start gliding) and query reach distance and shield/usable-item state.

## `world` — Raycasts, block placement checks, rotation math, Minecraft accessors.

- **BlockUtil** — Block queries for combat placement: clickable blocks, crystal spots, entity occupancy, nearby block scans, respawn-anchor charges and cobweb-in-line-of-sight checks.
- **ClientChunkManagerAccessor** — Reflective accessor for ClientChunkManager's private chunk map.
- **ClientChunkMapAccessor** — Reflective accessor for ClientChunkMap's private chunk array.
- **CobwebSwitchBackTask** — Deferred task queued on the rotation handler that retries CobwebPlacer's hotbar switch-back on the next tick and re-queues itself while it is still pending.
- **CountPlacementModifierAccessor** — Reflective accessor for CountPlacementModifier's count provider (Ore Simulation).
- **DifficultySwitchMap** — Compiler-generated switch table mapping Difficulty ordinals to case numbers (used by DamageUtil).
- **HeightContextAccessor** — Reflective setter for HeightContext's minY and height fields (used when a HeightContext is built without a chunk generator).
- **HeightRangePlacementModifierAccessor** — Reflective accessor for HeightRangePlacementModifier's height provider (Ore Simulation).
- **HitParticlePhysics** — Base motion model for Hit Effect particles (legacy/pop variants): advances a particle and provides the passable-block test used for its block collisions.
- **LoadedChunkIterator** — Iterator over the client world's loaded chunks (read through the chunk-map accessors), optionally skipping chunks whose four neighbours are not loaded yet.
- **MeshBuilder** — Chainable mesh-building interface implemented by render/TextRenderer (MappedMeshBuilder): writes positions, colors, float attributes and indices, tracks a base-vertex offset and generates indices per GL primitive.
- **OreDefinition** — Ore-generation parameters of one placed ore feature (generation step, feature index, count, height range, rarity, vein size, discard-on-air chance, targets) plus display name and color, used by Ore Simulation.
- **PositionUtil** — Static helpers for entity positions (current/last tick), elytra gliding, game-profile names and a player's height above the ground.
- **RarityFilterPlacementModifierAccessor** — Reflective accessor for RarityFilterPlacementModifier's chance (Ore Simulation).
- **RaycastContextAccessor** — Reflective setter for RaycastContext's private start/end/shape/fluid/shape-context fields so one context object can be reused for many raycasts.
- **RaycastUtil** — Finds a point on a block's faces (or only its top face) that the player can see and reach within 4.25 blocks without blocks or entities in the way.
- **RotationUtil** — Rotation math: yaw/pitch toward points (optionally yaw-limited or jittered), angle distances, smoothed camera rotation, rotation direction vectors and raycasts along a rotation.
- **SimulatedBowArrow** — Client-only arrow entity ticked manually by the Trajectories module to trace bow/crossbow flight paths, recording whether the arrow hit an entity.

## `math` — Math helpers, particles, interpolation.

- **AutoCrystalRenderer** — Renders AutoCrystal's animated, fading target-block box that glides towards the current placement position.
- **BlockChangeTracker** — Tracks recently cleared blocks, explosions and our own block placements so anchor modules avoid acting on stale or just-exploded positions.
- **HitParticle** — A glow particle spawned by the HitEffect module on hits/totem pops, moved by a mode-specific motion model.
- **JumpCircle** — A ground ring spawned by the JumpCircles module where a player jumped, with its spawn time and lifetime.
- **LegacyParticlePhysics** — Legacy hit-particle motion model: slow gravity, damping and block-bounce collisions.
- **MotionState** — One predicted tick of the local player's motion (eye position, velocity, on-ground) used by DiagonalPearlCatch.
- **ObbyCrystalCandidate** — A scored AutoObbyCrystal placement candidate: base block, obsidian support block/side, aim point and whether obsidian must be placed or a crystal broken.
- **PearlLandingMarker** — Predicted landing point of a thrown ender pearl with its flight time and fade alpha, drawn by the PearlTrajectory module.
- **PopParticlePhysics** — Totem-pop hit-particle motion model: stronger gravity, damping and block-bounce collisions.
- **RenderCacheUtil** — Render helpers for LRU caches, quarter-step quantized cache keys and ARGB channel unpacking.
- **SettingValueParser** — Parses and validates textual config values and applies them to module settings of every setting type.
- **TexturedDrawModeKey** — Cache key (texture, corner radii, blend mode, depth test) for RoundedTextureShader draw modes.
- **TrailMark** — A single trail mark (footstep, ring or particle) spawned by the Trails module with its lifetime, yaw and motion.
- **TrailTracker** — Per-player state of the Trails module: last position a mark was dropped at and which foot steps next.
- **TwoDEspBox** — Animated screen-space box of a 2D ESP target (bounds, fade alpha, last frame seen, target snapshot).
- **UiMath** — UI math helpers (accent color, alpha, clamp, smooth approach, decimal flooring, special key names) plus an unused positioned-label component.
- **Vec3dMutator** — Reflectively overwrites the final x/y/z fields of a Vec3d so it can be reused as a scratch vector.
- **WeatherParticle** — A rain/snow/firefly/ember particle of the Weather module with position, velocity, age and flicker phase.

## `network` — Packet sending/queueing, ping and server-tick tracking.

- **BlockChangeListener** — Packet listener feeding BlockChangeTracker with cleared blocks, explosion centers and our own block placements, pruning old entries.
- **InteractItemPacketAccessor** — Reflectively overwrites the yaw/pitch of a PlayerInteractItemC2SPacket for silent rotations.
- **InventorySwapQueue** — Queues inventory slot-swap clicks until the player is stationary and not sprinting server-side, holding movement input meanwhile and running completion callbacks.
- **PacketSender** — Sends a packet through the client's network handler.
- **SilentSlotManager** — Silently switches the server-side selected hotbar slot, blocks vanilla slot packets while spoofing and restores the real slot before interactions.
- **TotemPopManager** — Counts totem pops per player from entity-status packets, clearing counts on death, despawn and world join.

## `render` — Custom renderer: shaders, GPU buffers, 2D/3D drawing, fonts, ESP outlines.

- **ApplyCallbackState** — Pipeline state that runs a callback when the render pass is applied (e.g. to bind a texture).
- **AuraOutlineShader** — ESP post shader for the 'Aura' mode: draws entity models into the mask texture and composites it with aura_outline.frag (crisp core line plus soft halo).
- **AxisSwitchMap** — Compiler-generated switch map over Axis.
- **BlurredRoundedRectShader** — Frosted-glass panel renderer: draws a rounded rectangle that samples the GaussianBlur of the main framebuffer (rounded_texture_blurred.frag), caching one blur per blur radius and one DrawMode per corner-radius set.
- **BoundMethod** — MethodAccessor bound to a target instance so it can be invoked with just the arguments.
- **BufferSource** — Per-DrawMode pool of GPU vertex/index buffers (like vanilla's VertexConsumerProvider.Immediate): hands out a mesh builder per draw mode during a render event and draws every requested mode on flush.
- **CallbackDrawCommand** — Draw command whose vertices are produced by a lambda (depth, builder), for a given pipeline and optional scissor rect.
- **ChamsOutlineShader** — ESP post shader for the 'Chams' mode: composites the entity mask with chams_outline.frag as a flat translucent fill with a darker rim.
- **ConfigReport** — Counters collected while saving or loading a config (sections, values, warnings, errors) with a compact summary string.
- **ConfigureCallbackState** — Pipeline state that runs a callback on the render-pass builder before the pass is built.
- **CooldownProgress** — Progress value (0..1) of the current item use or attack cooldown shown by the cooldown indicator HUD element.
- **CoreShaders** — Shared shader programs: position-color, position-color-texture, rounded rect, gaussian blur and MSDF text.
- **CornerRadii** — Rounded-rect corner radii quantized to 0.25 px (shader component order x=top-right, y=bottom-right, z=top-left, w=bottom-left); hashable so it can key the rounded-rect pipeline cache.
- **CornerRadiiUniform** — Pipeline state that uploads quantized per-corner radii to the rounded_pos_color shader's 'roundness' vec4 uniform.
- **DamageSample** — Timestamped amount of health lost, kept by AutoTotem to estimate the recent damage rate.
- **DelayTimer** — One-shot timer that reports once a fixed delay (ms) has passed since creation; SafeAnchor keeps one per anchor as a randomized reaction delay.
- **DrawCommand** — Deferred 2D draw command: emits its vertices into a vertex builder at a given depth and exposes the batch key (pipeline + scissor) used for batching.
- **DrawCommandQueue** — Deferred 2D draw queue: collects draw commands with increasing depth, batches consecutive commands sharing pipeline and scissor, and draws them from shared vertex/index buffers.
- **DrawPipeline** — Immutable draw pipeline: vertex format, GL primitive mode, entity-mask flag and the pipeline states that are configured, applied and reset around each indexed draw.
- **DrawPipelines** — Predefined DrawModes for the pos_col shader (triangles/lines with or without depth test and culling) plus a cache of rounded-rectangle DrawModes keyed by corner radii.
- **EntityTypeSet** — Thread-safe set (with a read-only view) of the entity types selected as shader-ESP targets.
- **EspShaderManager** — Global state of the shader ESP: active post shader, enabled/force-invisible flags, target entity types and caches mapping entity model classes to those types; hooks the shader into the render events.
- **FontManager** — Loads and caches MSDF fonts by name; the default font is 'inter'.
- **FullscreenPassChain** — Ordered list of fullscreen shader passes (shared static screen quad), each with a target framebuffer and a uniform-setup callback; used for post-processing (outlines, blur, storage glow).
- **GameRendererAccessor** — Reflective accessor for GameRenderer's overlayRenderer field.
- **GaussianBlur** — Two-pass separable Gaussian blur of Minecraft's main colour buffer into its own textures (horizontal then vertical pass), re-run at most once per frame or on resize; its output texture backs the frosted-glass HUD panels.
- **GlBuffer** — Cleaner-managed OpenGL buffer object (bound as GL_ARRAY_BUFFER) supporting allocation, mapped ranges and unmapping.
- **GlFramebuffer** — Cleaner-managed OpenGL framebuffer object with helpers to attach textures and clear its color buffer.
- **GlPrimitiveSwitchMap** — Compiler-generated switch map over GlPrimitive used by the vertex builder's index generation, plus that interface's assertion flag.
- **GlRenderPass** — One OpenGL draw pass: binds a target framebuffer, optionally clears colour/depth, uses a shader program, uploads the projection/view matrices and issues indexed draws.
- **GlRenderPassBuilder** — Builder for a RenderPass: target framebuffer (id or supplier), optional clear color/depth and the shader program.
- **GlStateSnapshot** — Snapshot of the OpenGL state (capabilities, program, VAO/buffers, texture bindings, blend, scissor, read/draw framebuffers) captured before custom rendering and restored afterwards.
- **GlTextureHandle** — Wrapper around an OpenGL texture object (owned and Cleaner-deleted, or wrapping an existing GL/Minecraft texture) with one-time storage allocation, sub-image uploads and filter/wrap parameters.
- **GlyphQuadCommand** — Draw command for one textured MSDF glyph quad: screen rect, atlas UVs, ABGR color and optional transform matrix.
- **GlyphVisitor** — Callback invoked for each glyph while iterating a string; returning false stops the iteration.
- **GuiRendererAccessor** — Reflective accessor for GuiRenderer's guiProjectionMatrix field.
- **JumpKeyPresser** — Presses the jump key programmatically for two ticks (used by elytra/wind-charge modules) and releases it again unless the player is physically holding it.
- **JumpKeyReleaser** — Client-tick listener for JumpKeyPresser that counts down the forced jump press and releases the key when it expires.
- **KeyBindingAccessor** — Reflective accessor for a KeyBinding's bound key.
- **MappedMeshBuilder** — Mesh builder (the only WorldContract implementation) that writes vertex attributes and indices straight into mapped GPU vertex/index buffers, growing them on demand and tracking a base vertex for index generation.
- **MinecraftAccess** — Interface exposing the MinecraftClient instance as the constant 'mc' to modules, HUD elements, managers and render helpers.
- **ModuleToast** — Toast entry of the module-notifications HUD element: title, message, type, creation time, fade animation and dismissed flag.
- **MouseAccessor** — Reflectively invokes Mouse.onMouseButton to simulate mouse button presses and releases.
- **MsdfFont** — MSDF atlas font loaded from assets/prestige/font/<name>.json/.png; measures text and queues glyph quads into the deferred 2D draw queue.
- **Notification** — Animated on-screen notification (icon, title, message, timed progress bar) drawn and stacked by the notification manager.
- **NotificationTypeSwitchMap** — Compiler-generated switch map over NotificationType.
- **OutlineShader** — ESP post shader for the 'Normal' mode: composites the entity mask with outline.frag (glowing outline with optional fill).
- **PearlThrowSolution** — Best pearl throw found by DiagonalPearlCatch: throw tick, yaw/pitch, closest miss distance and the target-path tick of closest approach.
- **PostShader** — Base class of the ESP post-processing shaders: entity models are drawn into an offscreen mask texture, which is then composited onto the screen by a full-screen pass with shader-specific uniforms.
- **ProjectionMatrixAccessor** — Reflectively calls ProjectionMatrix2.getMatrix(width, height).
- **QueuedSlotSelect** — Deferred hotbar slot selection run by the rotation manager, limited to one slot switch per tick (re-queues itself otherwise).
- **QueuedSwapClick** — Inventory SWAP click (slot, hotbar button, owner, completion callback) queued by PacketQueue until it can be sent.
- **RainbowOutlineShader** — ESP post shader that composites the entity mask with rainbow_outline.frag (animated rainbow outline and glow).
- **RandomUtil** — Shared Random plus helpers for a random float/int within a range (used for humanised delays, chances and jitter).
- **Render2D** — Static 2D/overlay render helpers: per-frame projection/model-view/camera matrices and viewport, world-to-screen projection, tick interpolation, wave colors and convenience draw calls (rects, circles, textures, item icons).
- **RenderCallback** — Listener type of RenderEvents, called with the per-pipeline buffer cache and the camera matrices.
- **RenderCore** — Core of the custom renderer: GL-resource Cleaner, global BufferSource, render-thread task queue and executor, frame counter, main-framebuffer queries, camera-relative transforms, and dispatch of the world/HUD render events.
- **RenderEvents** — Fabric events fired by the render hooks (2D/HUD pass, world pass, and after the HUD pass), each receiving the per-pipeline buffer cache and the camera matrices.
- **RenderLayerAccessor** — Reflective accessor for a RenderLayer's renderSetup field.
- **RenderLayerUtil** — Helpers for the entity-outline ESP hook: reads the GL texture id of a vanilla RenderLayer and tees a model's VertexConsumer into the ESP outline shader's buffer.
- **RenderStateElement** — One piece of GL pipeline state of a DrawMode (target framebuffer, shader, capability toggle, blend, depth mask/func, scissor, uniforms): configures the pass builder, applies before the draw and restores afterwards; static factories create the standard elements.
- **Rotation** — Yaw/pitch rotation, flagged as clamped when the yaw step towards the target was limited by the maximum rotation speed.
- **RotationProvider** — Implemented by modules that want to control the player's rotation; the rotation manager polls registered providers by priority every tick.
- **RoundedOutlineShader** — Draws anti-aliased rounded-rectangle outlines (rounded_outline_pos_color.frag) on the 2D draw queue, emitting only the four edge strips when the rectangle is large enough.
- **RoundedRectShader** — Draws filled anti-aliased rounded rectangles (rounded_pos_color shader, SDF corners) on the 2D draw queue, caching one DrawMode per corner-radius set.
- **RoundedTextureShader** — Draws textures inside rounded rectangles with a tint blend mode (rounded_texture shader): 2D icons/sprite regions on the draw queue and world-space (optionally per-corner tinted) quads through the BufferSource.
- **ScheduledClick** — Simulated mouse click scheduled by AimManager: button, creation time, press delay, hold duration and whether it is already pressed.
- **Shader** — Linked OpenGL shader program handle (freed via the Cleaner) with cached projMat/viewMat uniform locations and uniform helpers.
- **ShaderBase** — Abstract base for renderers that own one GLSL program loaded from a vertex/fragment resource pair; also exposes the client and a start timestamp for time-based uniforms.
- **ShaderCompiler** — Compiles GLSL vertex/fragment sources from the classpath and links them into a Shader program (SPIR-V path removed).
- **ShapeRenderer** — Static shape-drawing API: world-space paths, discs, glow rings and boxes written into the BufferSource, and 2D triangles, rects, (gradient) rounded rects, circles and rings queued on the shared 2D draw queue; also ARGB->ABGR colour helpers.
- **SilentRotationManager** — Server-side (silent) rotation manager (Prestige.o): collects rotation requests from registered providers, applies them to movement packets/travel/jump and item-use packets, supports an owner-held rotation lock, tracks whether an action was already performed this tick and runs scheduled tasks and hotbar switches on the next tick.
- **SnapGuide** — HUD-editor snap guide line along one axis at a fixed coordinate.
- **StopWatch** — Millisecond stopwatch used for click/place delays (fixed or randomized via a RangeSetting).
- **StorageOutlineRenderer** — StorageESP glow effect: renders storage-block geometry into an offscreen mipmapped framebuffer and composites it onto the screen with the storage_outline post shader (quality/radius/fill).
- **SwapBackTracker** — Global owner-tracked record of the hotbar slot that was selected before a module started swapping, so the owning module can swap back when it is done.
- **TextureLoader** — Loads PNG/JPEG classpath resources into GL textures via STBImage, with optional mipmaps and anisotropic filtering.
- **ThreatAssessment** — Most dangerous nearby damage source (crystal/anchor/obsidian/mace) with raw and confidence-weighted damage, used for totem decisions.
- **ToggleAnimation** — Time-based eased animation between an off and an on state that reverses smoothly from its current progress when toggled.
- **VertexArray** — Cleaner-managed OpenGL vertex array object that tracks and updates its vertex attribute bindings.
- **VertexLayout** — Vertex layout: ordered attribute elements with computed byte offsets and stride; creates per-attribute buffer bindings for a VAO.
- **VertexLayouts** — Predefined vertex layouts matching the shaders (position, position+colour, +uv, +normal, rounded-rect uv/size/colour, fullscreen uv).
- **VirtualKeyMapper** — Translates Windows virtual-key codes (how keybinds are stored) into GLFW key codes.

## `hud` — Draggable HUD widgets.

- **CartHelperElement** — Cart-PvP HUD panel showing trigger readiness (rail, TNT minecarts and a bow or charged crossbow in the hotbar), the crossbow charge state/bar and the TNT-minecart stock.
- **CooldownIndicatorElement** — Themed cooldown bar under the crosshair that replaces the vanilla attack indicator and shows attack cooldown, item cooldowns and bow/crossbow/trident charge with fade and ready-flash animations.
- **CrosshairElement** — Custom crosshair widget that cancels the vanilla crosshair and draws an accent-colored dot and pulsing ring that flashes white while the aimed-at entity is hurt.
- **FPSElement** — HUD panel showing the client's frame rate with a smoothed, color-graded value and a 24-sample history bar graph.
- **HudElement** — Base class of every draggable HUD widget: stores its name, enabled toggle and screen rectangle, resolves an edge/center anchor + offset layout (persisted in the config), and implements the HUD-editor row, dragging with snap guides and keyboard nudging.
- **KeybindsElement** — HUD panel listing every enabled module that has a keybind, each row showing the key name and module name with fade-in/out animations.
- **LatencyElement** — HUD panel showing the player's own ping from the tab list with a smoothed, color-graded value and a 24-sample history bar graph.
- **ModuleListElement** — Array-list HUD widget showing all enabled modules sorted by name width, with slide/fade animations and a brief highlight when a module gets enabled.
- **ModuleNotificationsElement** — Toast-notification HUD widget (global instance) that stacks module toggle and module status messages with type icons, colors and a lifetime bar.
- **OffscreenArrowElement** — Draws a glowing arrowhead around the screen center pointing toward the closest player while that player is outside the view cone.
- **PotionDisplayElement** — HUD panel listing the player's active status effects with roman-numeral level badges, remaining duration (blinking when low) and a duration progress bar.
- **ResourcesElement** — HUD panel counting gapples, totems, end crystals and XP bottles in the inventory and raising a warning toast when a count drops below its threshold.
- **TPSElement** — HUD panel estimating the server tick rate from the interval between WorldTimeUpdate packets, with a smoothed, color-graded value and a 24-sample history graph.
- **TargetElement** — Target HUD card for the current combat target (or yourself in the editor) with skin face, name, distance, armor/held items and an animated health bar with damage trail and hurt flash.
- **TimeElement** — HUD clock panel showing the weekday, HH:MM with a blinking colon, small seconds and a bar tracking progress through the current minute.
- **WatermarkElement** — HUD watermark panel with the client logo and a pulsing accent-colored "Prestige Client" title.
- **WebsiteElement** — HUD panel showing the client website address in a pulsing accent color.

## `gui` — ClickGUI / HUD-editor screens.

- **BaseScreen** — Minimal Minecraft Screen with an empty title that Prestige's own screens extend.
- **ClickGuiScreen** — Placeholder Minecraft screen for the ClickGUI: on first render it opens the native GUI overlay and it closes itself (and the overlay) when the overlay is dismissed or the screen is closed.
- **HandledScreenAccessor** — Reflective accessor that reads a HandledScreen's private focusedSlot (the inventory slot under the mouse).
- **HudEditorScreen** — Owner of all HUD elements and the HUD editor screen: renders enabled elements in-game and, while editing, draws the grid, the draggable element-list panel, snap guide lines and key hints, handling dragging, toggling, arrow-key nudging and layout reset.
- **LockedInventoryScreen** — InventoryScreen variant that ignores mouse clicks and hotbar number keys, opened by AutoTotem so the player cannot interfere with its scripted totem slot clicks.

## `config` — Config (de)serialisation and resource loading.

- **ClasspathResource** — Normalized classpath resource path (e.g. a font's .json/.png under assets/prestige) that can open itself as an InputStream from the mod jar.
- **ClasspathResourcePack** — Stub ResourcePack whose static factories wrap mod-jar classpath files as Minecraft Resources/InputSuppliers, used by resource-manager mixins to inject Prestige shaders and assets.
- **ConfigManager** — Serializes the Menu, every module's settings/enabled state and the HUD layout into a JSON snapshot and applies such snapshots back (size/depth checks, lifecycle rollback, unknown-key warnings), reporting results as a config toast.
- **MsdfFontMetrics** — Font-wide metrics (em size, line height, ascender/descender, underline position/thickness) parsed from the 'metrics' block of an msdf-atlas-gen font JSON for the MSDF text renderer.
- **MsdfGlyph** — One glyph entry of an msdf-atlas-gen font JSON: its unicode code point, advance, and plane (layout) and atlas (texture) bounds used to build glyph quads.

## `reflect` — Reflection/MethodHandle access to private Minecraft members.

- **BoundField** — A FieldAccessor bound to one target instance, exposing get() and set(value).
- **CameraAccessor** — Wraps a Camera and calls its protected setPos/setRotation through statically cached, remapped MethodAccessors.
- **FieldAccessor** — Fast get/set accessor for one resolved (possibly private/final) field using Unsafe offsets, a VarHandle or plain reflection, with factories that remap Yarn field names to runtime names and can bind it to an instance.
- **FieldCacheKey** — Cache key (owner class, Yarn field name, field type, target runtime class) for remapped FieldAccessor lookups.
- **MappedField** — Field handle resolved once from a Yarn field name (kept in static finals) offering fast Unsafe/VarHandle/reflective setDouble/setFloat/set/get, falling back to a per-call remapped lookup when resolution failed.
- **MethodAccessor** — Invoker for a resolved (possibly private) method through a cached MethodHandle spreader, with factories that remap Yarn method names to runtime names, cache the results and bind to an instance.
- **ReflectionBase** — Shared reflection core for the accessors: obtains sun.misc.Unsafe, forces accessibility, strips final modifiers, parses type descriptors and caches resolved fields and methods across class hierarchies.
- **ResolvedField** — Immutable result of resolving a field: the reflective Field, whether it is static, its Unsafe static base and offset, and an optional VarHandle.
- **ResolvedMethod** — Immutable result of resolving a method: the reflective Method, its unreflected MethodHandle and whether it is static.

## `data` — Small records and value holders.

- **AnchorCandidate** — AutoAnchor candidate anchor position with placement click, charge count, distances to the target and a heuristic score.
- **AnchorCatchPearlLanding** — AnchorPearlCatch prediction of where an enemy ender pearl lands and in how many ticks.
- **AuraCartPlacement** — CartAura record of a TNT minecart the player placed (position and time).
- **AutoAnchorPlaceTarget** — AutoAnchor placement click: the neighbouring block to click and the hit vector on its face.
- **AutoAnchorPlan** — AutoAnchor plan: chosen anchor position with its placement click and an optional blocker that shields the player.
- **BlendFuncState** — Render-state element that enables blending with a custom separate blend function and restores the blend flag afterwards.
- **BlendToggleState** — Render-state element that enables standard alpha blending (or disables blending) for a pass and restores it afterwards.
- **CameraMatrices** — Matrices for a render pass: projection, model-view and the camera (null for the 2D HUD pass).
- **CartBlockerPlacement** — AutoSafeCart candidate block placement that shields the player from a TNT minecart, with the resulting self damage.
- **CartCatchPearlLanding** — CartPearlCatch prediction of where an enemy ender pearl lands and in how many ticks.
- **CartThreat** — AutoSafeCart threat: a TNT minecart, where it would explode, its horizontal speed and the damage it would deal.
- **DelayedPacket** — Backtrack packet held back until its release time.
- **DepthFuncState** — Render-state element that sets glDepthFunc for a pass and restores the previous function afterwards.
- **DepthMaskState** — Render-state element that sets glDepthMask for a pass and restores the previous value afterwards.
- **DimensionChunkKey** — OreSimulation map key: dimension registry key plus packed chunk position.
- **DrawBatchKey** — Key that groups queued 2D draws into one batch: optional scissor rectangle and draw mode.
- **DrawModeBuffers** — Per-draw-mode GPU resources of the batching buffer source: vertex/index buffers, vertex array and the builder currently writing into them.
- **ExplosionDamageKey** — Per-tick cache key for explosion damage calculations (target, explosion position and calculation options).
- **FramebufferBindState** — Render-state element that selects the target framebuffer (via an id supplier) for a pass.
- **GlCapabilityState** — Render-state element that glEnables/glDisables a capability (cull face, depth test, ...) for a pass and restores it afterwards.
- **KeybindListEntry** — Row of the keybinds HUD element: module, bound key name and fade-in alpha.
- **MeshInfo** — Size of a built mesh: vertex layout, vertex count and index count.
- **MethodLookupKey** — Cache key for reflective method accessors (owner, name, return type, parameter types, receiver class).
- **MsdfFontState** — Render-state element that binds an MSDF font atlas texture and uploads its unitRange uniform.
- **OutlineCaptureConsumer** — VertexConsumer that copies positions and UVs of vanilla geometry into the outline shader mesh with a fixed mask color.
- **PearlCatchAnchorPlan** — AnchorPearlCatch plan: where to place/charge the anchor near the pearl landing and an optional blocker that shields the player.
- **PearlCatchPlaceTarget** — AnchorPearlCatch placement click: the neighbouring block to click and the hit vector on its face.
- **PearlFollowLanding** — PearlFollow simulation result of a pearl throw: landing position, flight ticks and peak height.
- **PearlThrowOption** — PearlFollow candidate pearl throw: pitch, landing distance to the followed pearl, flight time and peak height.
- **PendingModuleActivation** — Config loading: a module whose saved Enabled state is applied after all settings have been loaded.
- **ProgramUniforms** — Fluent uniform setter for a GL shader program with a per-program cache of uniform locations.
- **QueuedCrystalAttack** — AutoCrystal crystal attack queued for a later tick together with the rotation it was aimed with.
- **QueuedDraw** — A queued 2D draw call with the depth value that keeps submission order when batches are flushed.
- **QueuedRenderPass** — A configured render pass queued in a vertex buffer together with the callback that draws into it.
- **RoundedOutlineKey** — Cache key for rounded-outline draw modes: per-corner radii and outline thickness.
- **SafeCartPlacement** — AutoSafeCart record of a TNT minecart the player placed (position and time) so own carts are not treated as threats.
- **ScissorState** — Render-state element that sets (or disables) the GL scissor rectangle for a pass and restores the scissor flag afterwards.
- **ShaderBindState** — Render-state element that selects the shader program used by a pass.
- **StorageBlockMarker** — StorageESP marker for one storage block: block coordinates and the packed ABGR color to draw it with.
- **TrajectoryPath** — Simulated projectile path for the Trajectories module: the points and whether the projectile hit something.
- **UniformCallbackState** — Render-state element that hands the pass shader program's uniform setter to a callback.
- **VertexAttributeBinding** — Binding of one vertex attribute to a GPU buffer (element format, stride and byte offset) inside a vertex array object.
- **VertexElement** — One element of a vertex layout: component count, GL component type, bytes per component and normalization flag.

## `type` — Enums.

- **AnchorCatchState** — State machine of the AnchorPearlCatch module.
- **AnimationMode** — Animation style of the HitEffect module.
- **ArmorState** — Armor swap state of the AutoTotem module (none, equip, restore).
- **AutoAnchorState** — State machine of the AutoAnchor module.
- **AutoCartState** — State machine of the AutoCart module.
- **BlendMode** — Color blend mode for textures/shaders (multiply, overlay, replace, add, screen).
- **CartCatchState** — State machine of the CartPearlCatch module.
- **CartIgnitionMode** — How AutoCart ignites the TNT minecart: wait passively or shoot it with a bow.
- **CobwebPlaceState** — Placement progress of the CobwebPlacer module.
- **DiagonalPearlState** — State machine of the DiagonalPearlCatch module.
- **DoubleAnchorState** — State machine of the DoubleAnchor module.
- **Easing** — Standard easing curves (sine, cubic, quad, ..., back) applied to a 0..1 progress value.
- **EventPhase** — Phase of an event: PRE, NONE or POST.
- **FastPearlCatchStage** — Stages of the FastPearlCatch module.
- **GlPrimitive** — Primitive type of a mesh draw (quads, triangles, fan, line strip, lines).
- **GuideAxis** — Axis of a HUD-editor snapping guide line.
- **HitFlickState** — State machine of the HitFlick module (idle, flick, attack).
- **HudAlignment** — Horizontal/vertical alignment of a HUD element (start, center, end).
- **HudAnchor** — Screen corner a HUD element is anchored to.
- **NotificationType** — Notification severity with its accent color.
- **ObbyCrystalState** — State machine of the AutoObbyCrystal module.
- **ScoopState** — State machine of the AntiWeb module (place water, wait, scoop it back).
- **ShaderMode** — ESP outline style and the post-processing shader that renders it.
- **SwapQueueResult** — Outcome of an inventory swap request: done immediately, queued or rejected.
- **ThreatType** — Kinds of incoming damage threats (crystal, anchor, obsidian+crystal, mace) with a confidence weight for totem prediction.
- **XbowCartState** — State machine of the XbowCart module.

## `social` — Friends and Mojang profile lookups.

- **FriendManager** — Stores friend names (teammates count as friends) and lists online non-friend players.
- **MojangApi** — Looks up player UUIDs and signed game profiles (with skin properties) from the Mojang web APIs.

## `input` — Mouse/keyboard input handling.

- **KeybindManager** — Turns key and mouse-button events into module keybind toggles, opens the click GUI on the menu key (or when the native side requests it) and applies a pending GUI scale change each frame.

## `annotation` — Marker annotations left by the original build.

- **Chainable** — Marker annotation for API methods that the native module calls/chains into.
- **NativeId** — Annotation carrying the UUID that binds a Java stub to a function of the native module.

## `engine` — Loader glue: applies the ClassTransform hooks from inside Sponge Mixin (written for the open-source build).

- **EngineDev**
- **EnginePlugin**
- **EngineState**

## `ui` — Dear ImGui ClickGUI and the reflection bridge to the client (written for the open-source build).

- **ConfigsPage**
- **CreateConfigPage**
- **Data**
- **DetailPage**
- **DevCmd**
- **Glow**
- **Input**
- **KeybindsPage**
- **Lib**
- **LocalConfigs**
- **ModulesPage**
- **SettingsPage**
- **SocialsPage**
- **Theme**
- **ThemesPage**
- **Ui**
- **WidgetsPage**

## `ui/bridge`

- **Bootstrap**
- **Bridge**
- **DumpHook**

## `ui/mc`

- **Entry**
- **Icons**
- **Overlay**
- **Skid**

## `ui/mixin`

- **EventBusStatsMixin**
- **KeyboardHandlerMixin**
- **MouseHandlerMixin**
- **RenderSystemMixin**

`engine/mixin/I00..I41` and `N00..N41` are empty anchor mixins (one per hooked Minecraft class, intermediary/named); `EnginePlugin` addresses them by index, so they keep numeric names.
