# We Do Raids

**The official [We Do Raids](https://discord.gg/wdr) Discord plugin — approved by the official WDR Discord.**

Find, join and host **We Do Raids** raid recruitment for **Theatre of Blood, Chambers of Xeric and Tombs of Amascut** without leaving the game.

## What it does

- **Live recruitment feed** — the raid calls posted in the WDR Discord LFR channels appear in a side panel in real time (raid, tier, world, open spots / party size, roles, region, host).
- **One-click world hopping** — click a call's world to hop straight to it.
- **Party-board highlights** — WDR teams currently recruiting are highlighted on the in-game Theatre of Blood and Tombs of Amascut party boards.
- **Host your own call** — post a recruitment straight into the correct WDR channel (raid, tier, world, party size, spots, roles, party hub, description; CoX layout is auto-scouted), then tick roles as they fill or close it — the Discord post updates itself.
- **KC / tier gating** — you only see and can host in the channels your raid KC qualifies for, matching the WDR Discord's own tier structure.
- **Ban-list aware** — respects the WDR ban list.

## Getting started

1. Join the We Do Raids Discord and run the verify command in the verification channel to get your personal key.
2. Open the **We Do Raids** plugin config in RuneLite and paste the key into **Verification key**.
3. Log in on the account whose name matches your WDR nickname — recruitment calls appear in the side panel.

## Privacy / data

This plugin connects to the We Do Raids bridge (`wdr.timecapsule.ink`), the community's own server.

- **No bridge traffic occurs until you enter a verification key.** Demo mode is fully local and makes no bridge requests.
- When fetching the live feed, the plugin sends your **logged-in RuneScape name** (`viewer`) and **verification key** so the bridge can verify you and enforce the WDR ban list.
- When you choose to post, update, or close a raid, the bridge also receives entered `raid`, `tier`, `world`, `size`, `spots`, `roles`, `scale`, `fc`, `layout`, `partyHub`, and `desc` values, plus `messageId` for an existing post and your in-game name (`ign`/`viewer`).
- Your key is stored as a secret RuneLite configuration value. The plugin also stores your last raid and tier filter selections; it does not persist recruitment or host-post data.

## Affiliation

This plugin is developed in partnership with the We Do Raids team and is the community's official RuneLite integration.

## License

BSD 2-Clause — see [LICENSE](LICENSE).
