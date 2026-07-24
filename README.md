# We Do Raids

The official RuneLite plugin for the [We Do Raids](https://discord.gg/wdr) Discord. Find, join and host WDR raid recruitment for Theatre of Blood, Chambers of Xeric and Tombs of Amascut without leaving the game.

## What it does

- Shows the raid calls posted in the WDR Discord LFR channels in a side panel, updated about every 10 seconds. Each call lists the raid, tier, world, open spots, party size, roles, region and host.
- Click a call's world to hop straight to it.
- Highlights WDR teams that are currently recruiting on the in-game Theatre of Blood and Tombs of Amascut party boards.
- Lets you host your own call: post a recruitment into the correct WDR channel (CoX layouts are auto-scouted), tick roles as they fill, and close it when you're done. The Discord post keeps itself up to date.
- Filters the panel and hosting form by raid and tier. Filters are local; the bridge verifies eligibility and enforces the WDR ban list.

## Getting started

1. Join the We Do Raids Discord and run the verify command in the verification channel to get your personal key.
2. Open the We Do Raids plugin config in RuneLite and paste the key into "Verification key".
3. Log in on the account whose name matches your WDR nickname. Recruitment calls will appear in the side panel.

## Privacy and data

The plugin talks to the We Do Raids bridge (`wdr.timecapsule.ink`), the community's own server.

- Nothing is sent until you enter a verification key. Demo mode is fully local and makes no bridge requests.
- When fetching the live feed, the plugin sends your logged-in RuneScape name and verification key so the bridge can verify you and enforce the WDR ban list.
- When you post, update or close a raid, the bridge also receives the values you entered in the hosting form (raid, tier, world, party size, spots, roles, scale, fc, layout, party hub and description), the message id of an existing post, and your in-game name.
- Your key is stored as a secret RuneLite configuration value. The plugin also remembers your last raid and tier filter selections; it does not persist recruitment or host-post data.

## Affiliation

Developed in partnership with the We Do Raids team; this is the community's official RuneLite integration.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
