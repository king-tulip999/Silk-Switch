# Modrinth upload: Silk Switch

Everything to enter on modrinth.com, in the order Modrinth asks for it.

## 1. Create the project (modrinth.com → sign in → "+" → New project)

| Field | Value |
|---|---|
| Name | Silk Switch |
| URL | `silk-switch` (checked free on 2026-10-25) |
| Visibility | Public |
| Summary | Press a key to switch the tool in your hand between Fortune and Silk Touch. |

## 2. Project settings

| Setting | Value |
|---|---|
| Icon | `modrinth\icon.png` |
| Description | paste all of `modrinth\description.md` |
| License | MIT |
| Environment | Client and server, required on both |
| Categories | Utility, Game Mechanics |

## 3. Create a version (Versions tab → Create a version)

| Field | Value |
|---|---|
| File | `silkswitch-1.0.5.jar` (in the main project folder) |
| Version number | 1.0.5 |
| Version title | Silk Switch 1.0.5 |
| Release channel | Release |
| Loaders | Fabric |
| Game versions | 26.3 |
| Dependencies | Fabric API, required; Mod Menu, optional |
| Changelog | see below |

### 1.0.5 changelog

```markdown
**Silk Switch 1.0.5**

- **New swap effects.** Switching between Fortune and Silk Touch now looks like it:
  - Three strands of dust spiral up around you. The old enchantment's color spreads out while the new one's color closes in.
  - A spark runs once around the tool's hotbar slot.
  - The new enchantment's name slides in above the hotbar, pushing the old name up and out.
  - The slot flash and the amethyst chime are still there.
- **No more re-equip animation.** The tool stays in your hand when you swap instead of dipping out of view and coming back up.
- The dust burst that came off the pickaxe is gone, replaced by the spiral.
- The new effects only show on your own screen. Players nearby still hear the chime.
- On a server, update the mod on the server and every player at the same time. 1.0.4 and 1.0.5 can't talk to each other.
```

### 1.0.4 changelog (previous version)

```markdown
**Silk Switch 1.0.4**

- **No more free Fortune.** A tool that only ever had Silk Touch can no longer switch to Fortune III. Silk Switch now only gives back Fortune the tool really had.
- **New settings page.** With Mod Menu installed, open it from Mods → Silk Switch. Without Mod Menu, bind the new "Open Silk Switch settings" key in Controls.
  - **Fortune ⇄ Silk Touch:** turn the swap on or off.
  - **Fortune on Silk-only tools:** off by default. Turn it on to let tools that never had Fortune switch to Fortune.
  - **Fortune Level:** shows up when that option is on. Choose the level those tools get, from I to X (higher if a data pack raises Fortune's max level). Handy with mods like Unlimited Enchantments that allow Fortune above III.
- If a swap is refused, the action bar now tells you why.
- Tools you already switched to Silk Touch with older versions still get their Fortune level back.
- On a server, update the mod on the server and every player at the same time. Older versions and 1.0.4 can't talk to each other.
```

## 4. Optional but worth it

- **Gallery screenshot.** Run `gradlew runClient`, press G with an enchanted pickaxe and take a screenshot with F2. Pages with a screenshot get more downloads.
- **Source link.** If you put the code on GitHub, add the link under Links → Source.

## 5. Submit for review

The project stays a draft until you press **Submit for review**. Modrinth's moderators check every new project before it goes public. That usually takes a few days, and they email you if they want changes.
