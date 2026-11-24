# Bounty Tracker (Fabric, server-side)

Requires: Java 25, Fabric Loader 0.19.5+, Minecraft 26.3, Fabric API. Players do NOT need the mod installed.

## Commands (ops only)
- /tracker give <players> <target>   Give a tracker compass pointed at <target> to the selected players
- /tracker compass [players]         Give the reusable Tracking Compass (yourself if no players given)
- /tracker clear <players>           Remove tracker compasses from the selected players
- /bounty set <target> <item> [n]    Put a bounty on <target>; whoever kills them gets the item
- /bounty remove <name>              Cancel a bounty (works for offline names too)
- /bounty list
- /bounty clear

Examples:
  /tracker give @a Steve
  /tracker give Alex Steve
  /bounty set Steve minecraft:diamond 5

## Player Tracker (reusable)
Use `/tracker compass` to receive a reusable Player Tracker and open the online player picker. The picker shows each player's face; click a face to start tracking. Run `/tracker select` to open it again and change targets. `/tracker compass <player>` gives that player a tracker and opens their picker.

You can also give a reusable compass with vanilla `/give` (no mod is needed to hold it):

  /give @s compass[custom_data={bt_compass:1b}]

- Right-click: open the player picker.
- Sneak + right-click: stop tracking.
- A selected tracker is named "Player Tracker". You can choose another player any time.
- Natural deaths do not remove the tracker. A player killing the tracked player removes it.
- Name/lore are applied automatically on first right-click if you use the bare `/give` above.

## Behaviour
- Holding the compass shows "Name  123 m  ↗" above the hotbar; the arrow is relative to where you are facing.
- The compass needle also follows the target (same dimension only).
- When another player kills the target, every compass tracking them is removed.
- The reward is given to the player who kills the bountied player. Deaths from mobs/environment do not pay out; the bounty stays.
- Bounties are saved in config/bountytracker.json.

## Build
  gradle wrapper --gradle-version 9.6.0   (once)
  ./gradlew build
Jar ends up in build/libs/ (use the one without -sources).

## GitHub
Push this folder's contents to the root of a repo (the `.github` folder must be at the root).
- Every push/PR builds the jar; download it from the run's **Artifacts**.
- To publish a release: `git tag v1.0.1 && git push origin v1.0.1` — the jar is attached automatically.


## Player selection and death behavior

- Give a player the reusable compass with `/tracker compass <player>` (or `/tracker compass` for yourself).
- Right-click the Tracking Compass or run `/tracker select` to display a clickable list of online players. Select a name to begin tracking.
- Sneak + right-click stops tracking.
- When the tracked player dies, the tracking compass is removed from the holder's inventory.
- `/tracker choose <player>` can also be used to select a target while carrying a reusable Tracking Compass.

The player picker uses clickable chat entries so it works with this server-side-only mod and does not require a client installation.
