# Recipe Revealer

Reveals vanilla recipes in Minecraft’s recipe book. Click to fill ingredients; shift-click to fill the maximum. Missing ingredients show vanilla ghost previews.

**Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25 · Client only**

Download **recipe-revealer** from a successful [GitHub Actions run](https://github.com/EscapeAltDelete/Recipe-Revealer/actions) and put the JAR in `mods`. No Fabric API or configuration. Disable the recipe book’s craftable-only filter to see everything.

Works in singleplayer and multiplayer using ordinary inventory clicks. Server crafting rules still apply; stock vanilla recipes may differ from server datapacks. Covers crafting, furnaces, blast furnaces and smokers; adds no new UI.

GitHub builds, tests and publishes the mod to this repository’s Maven package, `recipe_revealer:recipe-revealer`. Three runtime classes; no bundled recipes, dependencies or tick loop. Test code is excluded from the JAR.

[PolyForm Noncommercial License 1.0.0](LICENSE). [Copyright 2026 EscapeAltDelete](NOTICE).
