# Create: Sentry Mechanical Arm

Equip mechanical arms with TaCZ firearms and connect automated defense, ammunition production, and spent casing recovery to Create logistics.

This is an unofficial repair and extension of [pihoue's Minecraft 1.21.1 port](https://github.com/pihoue/Create-SentryMechanicalArm1.21.1). It builds on that port rather than porting the original 1.20.1 mod again.

**Current version: 1.4.0-alpha.41 · Minecraft 1.21.1 · NeoForge**

## Features

### Automated defense and supply

- Sentry arms use TaCZ firearms to acquire, aim at, and engage targets. They require rotational power.
- Supply ammunition through ammo boxes, adjacent inventories, and TaCZ ammo depots.
- Coordinate sentries with the fire control block, target lists, and sentry scope.
- Supports dye colors and operation on Create contraptions, trains, and Sable structures.

### Casings, manufacturing, and reloading

- Identify recoverable ammunition from loaded gunpack data without registering a separate set of casing items for every pack.
- Casings retain their ammunition identity, use cropped ammunition textures by default, and appear in a dedicated creative tab.
- Make new casings with caliber molds, then reload individual rounds using fluid propellant, solid materials, and pressing.
- Automatic recipes derive material costs from the original ammunition recipes. Recovered casings save the materials needed to make a new case.
- JEI distinguishes casing, mold, and unfinished ammunition variants for recipe lookup.

### Casing collection

- **Sentries:** send shot byproducts to an adjacent Casing Collector, or one on the same moving structure. If no collector is available or storage is full, the remainder follows the ground-drop rules.
- **Players:** shot byproducts enter the inventory by default. Overflow drops using vanilla `/give` behavior. Direct ground drops can be enabled through configuration.
- The collector has 27 slots, supports hoppers and Create logistics, and can also receive custom shot byproducts defined by data packs.

### Data pack customization

Override manufacturing materials, fluid propellant amounts, new casing yields, and the type, quantity, and chance of shot byproducts.

**Explicit custom rules take priority over automatic derivation.** New casing manufacturing, reloading, and casing drops can each be disabled for individual ammunition types without editing the gunpack itself.

## Requirements

Required:

- Minecraft **1.21.1**
- Java **21**
- NeoForge **21.1.228 or newer**
- Create **6.0.10**
- Official TaCZ **1.2.0**

The current dependency declarations require these exact Create and TaCZ versions.

Optional:

- **JEI:** recipe display and lookup.
- **Create JEI Compat:** pagination for longer sequenced assembly recipes. **Version 1.0.3 is client-only; do not install it on a dedicated server.**
- **Sable / Create: Aeronautics:** physics structure gameplay. The tested environment used Sable 2.0.3 and Aeronautics 1.3.2.

Sable Companion 1.6.0 is bundled; no separate installation of that library is needed. Stationary sentries do not require Sable or Aeronautics.

## Installation and quick start

1. Install the required dependencies and place this mod's JAR in `mods`.
2. Install the same version on both client and server. Remove the old JAR when updating.
3. Supply rotational power to a sentry arm.
4. Hold a firearm, aim at the arm, and use the **TaCZ interaction key (O by default)** to equip it. Supply matching ammunition.
5. Open the relevant items' Ponder scenes for operation and production guidance. Use the current JEI recipes for exact materials and quantities.

Create, TaCZ, Aeronautics, and test gunpacks are not included. See the [installation guide](docs/RELEASE_INSTALL.md) and [full user guide](docs/MOD_GUIDE.md) for details. The detailed guides are currently in Chinese.

## Configuration and compatibility

Configuration covers casing registration, shot byproducts by shooter type, player inventory collection, automatic casing manufacturing, reloading, and material costs.

Startup configuration is stored in `config/sentrymechanicalarm-startup.toml`. World configuration is stored in the world's `serverconfig/sentrymechanicalarm-server.toml`. Disabling registration unregisters the related items, requires a restart, and must match on both sides. Existing related items cannot be retained while their registration is disabled.

Gunpack support uses generic rules. Energy ammunition, caseless ammunition, and ammunition with custom scripts may not be suitable for recovery or automatic manufacturing. Use explicit data pack rules when a recipe cannot be reliably derived. Compatibility with every gunpack is not guaranteed.

**This is an Alpha release.** Validation with two independent clients, extended sessions, and complex multi-ship scenarios is still incomplete. VS2 has not been tested and is not listed as verified support. Installing other casing-drop mods may produce duplicate byproducts.

## Documentation

The detailed documentation is currently in Chinese:

- [User guide and configuration](docs/MOD_GUIDE.md)
- [Installation and upgrading](docs/RELEASE_INSTALL.md)
- [Manufacturing cost rules](docs/COST_ALLOCATION.md)
- [Custom manufacturing and shot byproducts](docs/CUSTOM_MANUFACTURING.md)
- [Example data pack](examples/manufacturing-rules)
- [Building and test entry points](docs/BUILDING.md)

## Building from source

Use Java 21 and run from the repository root:

```sh
./gradlew build
```

On Windows, use `gradlew.bat build`. Output is written to `build/libs/`. Normal builds do not include the integration test code.

## AI assistance

Parts of this fork's code, textures, and documentation were created with AI assistance. The maintainer guides the design and participates in testing.

## Credits and license

This fork inherits the upstream port's code and original content, with additional fixes and features. Thanks to the original mod author, the port author, and the Create and TaCZ developers.

Licensed under the **MIT License**, retaining the upstream copyright notice. See [LICENSE](LICENSE). Third-party mods and gunpacks are not distributed with this project.
