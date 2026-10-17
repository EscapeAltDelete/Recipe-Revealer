# EscapeAltDelete

Shows vanilla recipes in Minecraft's own recipe book, without waiting for unlocks. Clicking a recipe fills the ingredients you own; shift-click fills as many as fit. Missing ingredients show Minecraft's normal ghost preview.

**Minecraft 26.3 · Fabric Loader 0.19.5+ · Java 25 · Client only**

Put the JAR in `mods`. No Fabric API, settings, extra screens, bundled recipes or tick loop. Works in singleplayer and on remote servers without installing anything on the server. Turn off the recipe book's craftable-only filter to see recipes whose ingredients you do not own.

Three small Java files load the built-in crafting/furnace/blast-furnace/smoker recipe displays once per connection and fill ingredients through vanilla inventory clicks. No guessed server recipe IDs, recipe merging or server-side unlocks. Server crafting rules and datapack changes still apply; this shows stock vanilla recipes, so a server's replacement recipe may differ. Ingredient tags use the connected world's registries. Workstations without an ordinary recipe book do not gain one.

## Downloads and builds

[GitHub Actions](https://github.com/EscapeAltDelete/Recipe-Revealer/actions) builds and tests the mod. Download the **EscapeAltDelete** artifact from a successful run. Successful main-branch runs also publish `escapealtdelete:escapealtdelete` in this repository's GitHub Packages Maven registry.

All builds run on GitHub using Java 25 and the checked-in Gradle wrapper. Dependency, build and configuration caches avoid repeated work. There is no source generation, shading, unnecessary clean build or obfuscation remapping. Superseded branch runs are cancelled. Each run publishes a unique package version with GitHub's job token.

CI runs `./gradlew build`, `xvfb-run -a ./gradlew -Pgametest runClientGameTest`, then `./gradlew publish`. The separate game test checks the recipe catalog, ignored recipe-book packets, repeated connections, shaped/shapeless placement, repeated clicks, shift-click, missing ingredients, cursor protection, furnace placement, and real server-side crafting results. Test code and its Fabric API dependency are not shipped.

## License

[PolyForm Noncommercial License 1.0.0](LICENSE). Copyright 2026 EscapeAltDelete. See [NOTICE](NOTICE).

## References

[Fabric's 26.3 example](https://github.com/FabricMC/fabric-example-mod/tree/26.3) establishes the toolchain. Minecraft's `ClientRecipeBook`, `RecipeDisplayEntry`, `PlaceRecipeHelper` and `StackedContents` supply the native representations and ingredient selection. The official Minecraft dependency in CI is the compile/runtime authority; a [26.3 source mirror](https://github.com/0khs/minecraft_source_26.3) was consulted during development.
