<div align="center">

# Kisde-Carpet

**A Minecraft 1.12.2 Carpet client companion for schematic sharing, inventory tools and displays.**

[![Minecraft 1.12.2](https://img.shields.io/badge/Minecraft-1.12.2-green)](https://github.com/froyln/Kisde-Carpet)
[![Java 8](https://img.shields.io/badge/Java-8-blue)](https://github.com/froyln/Kisde-Carpet)

</div>

---

## Getting started

1. Use Minecraft 1.12.2, Java 8 and LiteLoader, with MaLiLib 0.54.0, Litematica 0.31.4, Tweakeroo 0.31.0 and MiniHUD 0.41.1.
2. Set `JAVA_HOME` to a Java 8 installation. Run `./gradlew build --offline` with the pinned MaLiLib and Litematica binaries available in the instance mods directory. Pass `-PinstanceModsDir=/path/to/mods/1.12.2` to use another instance.
3. Install `build/libs/Kisde-Carpet.litemod` in `mods/1.12.2`. Remove the old `Bullet-Carpet.litemod` so only one companion loads.
4. Connect to the [Kisde-Carpet server](https://github.com/froyln/Kisde-Carpet). Its operator controls `schematicSave`, `schematicPaste`, `schematicPasteNoUpdates` and `pcaProtocol`.

An offline build requires the legacy Gradle/ForgeGradle dependencies to be cached already. A first build can use `./gradlew build` to resolve them. `setupDecompWorkspace` is not required.

## Usage

When connected to a Kisde-Carpet server with `schematicShare` enabled, open
Litematica's main menu and choose **Shared schematics** to browse, load, upload,
update, rename or remove server shares. All players can upload; authors and operators
can manage their shares. Each shared row has Load, Update, Rename and Remove
controls; placement rows also provide a Share button. Scroll with the mouse
wheel or drag the scrollbar to browse larger libraries. Loading a shared placement adds a local schematic
overlay and does not place blocks. See the server's [shared schematics guide](https://github.com/froyln/Kisde-Carpet/blob/staging/docs/usage/shared-schematics.rst).

Litematica Auto/server save captures server blocks and block entities, including inventories and sign text. Entities and scheduled ticks are not captured. Paste detects the server automatically and follows Litematica's command rate, layers, transforms and replacement settings. Use replacement `all` to restore NBT in existing blocks. Capture and paste require OP permission and creative mode. The server rules `schematicSave` and `schematicPaste` control container contents only: when off, blocks and other block-entity metadata remain available. The save screen disables **Save inventories** while `schematicSave` is off. With inventory paste off, new containers are empty and existing same-block container contents are preserved.

Tweakeroo and Litematica world inventory overlays refresh server contents while rendered. The schematic inventory is the saved inventory. MiniHUD uses the server's existing Carpet structure data.

The rename retains existing packet channels and internal compatibility identifiers. Without a paired server, ordinary Litematica client behavior remains available; vanilla paste does not preserve inventory NBT.

The companion keeps the **Total** entry above player rows in `/sb` statistics sidebars, including tied scores and temporary updates, and displays its name in bold. Stored names and score values are preserved; this display fix requires the companion on each viewing client.

The companion displays full anvil costs and honors `disableAnvilXpLimit`. Costs above 32767 levels require this updated companion; the server rejects those operations safely for older clients and still charges the full XP cost.

## Documentation

See the server's [integration guide](https://github.com/froyln/Kisde-Carpet/blob/staging/docs/usage/server-integrations.rst) for permissions, limits, no-update behavior and supported data. This companion is a separate client project; never install it on the server.
