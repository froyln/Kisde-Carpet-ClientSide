<div align="center">

# Kisde-Carpet

**A Minecraft 1.12.2 LiteLoader companion for server schematic capture, NBT paste, live inventory previews and MiniHUD structure boxes.**

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

Litematica Auto/server save captures server blocks and block entities, including inventories and sign text. Entities and scheduled ticks are not captured. Paste detects the server automatically and follows Litematica's command rate, layers, transforms and replacement settings. Use replacement `all` to restore NBT in existing blocks. Capture and paste require OP permission and creative mode.

Tweakeroo and Litematica world inventory overlays refresh server contents while rendered. The schematic inventory is the saved inventory. MiniHUD uses the server's existing Carpet structure data.

The rename retains existing packet channels and internal compatibility identifiers. Without a paired server, ordinary Litematica client behavior remains available; vanilla paste does not preserve inventory NBT.

The companion keeps the `Total` entry above player rows in `/sb` statistics sidebars, including tied scores and temporary updates. Names and score values are preserved; this display fix requires the companion on each viewing client.

## Documentation

See the server's [integration guide](https://github.com/froyln/Kisde-Carpet/blob/staging/docs/usage/server-integrations.rst) for permissions, limits, no-update behavior and supported data. This companion is a separate client project; never install it on the server.
