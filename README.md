# UhcCore Rebirth

UhcCore Rebirth is a maintained continuation of UhcCore, the UHC plugin
originally written by val59000 and later kept alive by Mezy and ZeroDind on
GitLab. Rebirth picks up where upstream stopped: it keeps the same plugin,
configs and gameplay, and carries it forward to Paper 1.21.x and the 26.x line
while still running on 1.8.8 servers.

UHC, or Ultra Hardcore, is a classic PvP gamemode in which players or teams
fight to be the last one standing in survival mode, with natural health
regeneration disabled. The only sources of healing are golden apples, potions
and other such items which grant status effects, so players must be careful
not to take unnecessary damage! The original gamemode was
[invented by the Mindcrack community][uhc-origins].

[uhc-origins]: https://www.reddit.com/r/mindcrack/comments/syqitq/the_origins_of_the_uhc_mod_10_years_ago/

## Features

- 40+ scenarios (Nether start, randomized drops, upside down crafting,
Dragon Rush, Superheroes and more), with an optional voting system
- GUI menu for team creation, no commands required
- Configurable start kits and crafting recipes
- Shrinking world border, deathmatch and world pre-generation
- `/uhc host`: a GUI menu where hosts can tweak team size, PvP time, border,
deathmatch, final heal, MeetUp rules and more before the game starts
(changes are kept in memory only, `config.yml` wins after a reload or restart)
- `/uhc latescatter`, `/uhc timer` and `/uhc revive` for running games by hand
- `/tl` (alias `/teamcoords`) to send your coordinates to living teammates
- Optional MeetUp surface rule for center deathmatch that pulls cave campers
and players outside the border back to the surface
- Built-in update checker that watches GitHub releases

See [CHANGELOG.md](CHANGELOG.md) for the details of each release.

## Compatibility

UhcCore Rebirth ships two jars. Pick the one that matches your server:

| Jar | Java | Server |
| :-- | :-- | :-- |
| `UhcCoreRebirth-Legacy-<version>.jar` | 8+ | Spigot/Paper 1.8.8 to 1.20.6 |
| `UhcCoreRebirth-Modern-<version>.jar` | 21+ | Paper 1.21.x and 26.x |

The Modern jar refuses to start below 1.21 and says so in the console. The
Legacy jar still loads on 1.21+, but logs a warning recommending the Modern jar.

The plugin is incompatible with world management plugins such as MultiWorld
or Multiverse, so run it on a standalone server.

## Download

Grab the jar for your server from
[GitHub releases](https://github.com/JainaGam3r45/UhcCoreRebirth/releases).

## Setup guide

1. Put the jar in the `plugins/` directory and start the server.
2. Set the `enable-uhc` option to `true` in `plugins/UhcCore/config.yml` (NOTE: This will erase player data, e.g. inventories).
3. Restart the server.
4. Wait for the world to pre-generate (see console output for progress).
5. Your players can now join! The game starts when enough players have joined
(according to the configuration file), or when the `/start` command is issued.

After running the plugin for the first time, configuration files will be
generated in the `plugins/UhcCore/` directory. Changes can be taken into
effect by restarting the server.

If you're having trouble, check [the FAQ](docs/content/faq.md).

## Bug reports and feature requests

Open an issue on
[GitHub issues](https://github.com/JainaGam3r45/UhcCoreRebirth/issues).
Be as descriptive as possible: the jar you use, your server version, and any
error messages or server logs.

## Contributing

Pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Building the plugin from source code

You need a Java Development Kit (JDK). The build uses Gradle toolchains
with Java 21. From the root of the repository, run:

**Windows**

```
gradlew clean build
```

**Linux and macOS**

```
./gradlew clean build
```

Both jars land in `build/libs/`:

- `UhcCoreRebirth-Legacy-<version>.jar`
- `UhcCoreRebirth-Modern-<version>.jar`

## Credits

- [JainaGam3r45](https://github.com/JainaGam3r45): maintainer and lead
developer of UhcCore Rebirth.
- [JiferCloud](https://www.jifercraft.com): hosting partner.
- **val59000**: original creator of UhcCore
([archived Bitbucket repository](https://archive.softwareheritage.org/browse/origin/directory/?origin_url=https://bitbucket.org/val59000/playuhc.git)).
- **Mezy**: maintainer before version `1.20.0`
([GitHub repository](https://github.com/Mezy/UhcCore/)).
- **ZeroDind**: maintainer of the upstream
[GitLab project](https://gitlab.com/uhccore/uhccore) up to `1.20.15`.

## License

```
Copyright (C) 2015 Valentin Baert
Copyright (C) 2017-2021 Pieter de Bot and others
Copyright (C) 2022-2025 Odin Dahlström and others

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
