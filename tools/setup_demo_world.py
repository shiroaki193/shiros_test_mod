"""Stages the visual demo world for `gradlew runDemoClient`.

Copies the superflat world left behind by `gradlew runGameTestServer` into run/saves/arms_race_demo
and installs tools/demo_datapack, which loops five waves, 20 s each:
  ciws: (night) mortar creeper vs. a villager guarded by two snow golems, side view
  cat:  (day) an iron golem fetches a cat and throws it at a creeper, side view
  aim:  (day) first-person, holding creeper shells: landing preview on a spot 31 blocks away
  long: (night) 120 block salvo at two snow golems, watched from 60 blocks to the side
  zombie: (night) three zombies walk at a villager guarded by two snow golems

Usage: gradlew runGameTestServer (once), then python tools/setup_demo_world.py [first wave]
  first wave: ciws | cat | aim | long | zombie (default ciws)
"""
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE_WORLD = ROOT / "run/gametestserver/gametestworld"
TARGET_WORLD = ROOT / "run/saves/arms_race_demo"
DATAPACK = ROOT / "tools/demo_datapack"
OPTIONS = ROOT / "run/options.txt"

# Keep the demo running while the window is unfocused, skip first-launch screens, stay quiet.
DEMO_OPTIONS = {
    "pauseOnLostFocus": "false",
    "onboardAccessibility": "false",
    "tutorialStep": "none",
    "soundCategory_master": "0.0",
}


WAVES = ["ciws", "cat", "aim", "long", "zombie"]


def main():
    first = sys.argv[1] if len(sys.argv) > 1 else WAVES[0]
    if first not in WAVES:
        sys.exit(f"unknown wave {first!r}; pick one of {WAVES}")
    if not SOURCE_WORLD.exists():
        sys.exit(f"{SOURCE_WORLD} not found - run `gradlew runGameTestServer` first")
    if TARGET_WORLD.exists():
        shutil.rmtree(TARGET_WORLD)
    shutil.copytree(SOURCE_WORLD, TARGET_WORLD, ignore=shutil.ignore_patterns("session.lock"))
    shutil.copytree(DATAPACK, TARGET_WORLD / "datapacks/mobarmsrace_demo")
    load = TARGET_WORLD / "datapacks/mobarmsrace_demo/data/mobarmsrace_demo/function/load.mcfunction"
    with load.open("a", encoding="utf-8") as f:
        f.write(f"scoreboard players set #wave mar_demo {WAVES.index(first)}\n")

    if OPTIONS.exists():
        lines = OPTIONS.read_text(encoding="utf-8").splitlines()
        seen = set()
        for i, line in enumerate(lines):
            key = line.split(":", 1)[0]
            if key in DEMO_OPTIONS:
                lines[i] = f"{key}:{DEMO_OPTIONS[key]}"
                seen.add(key)
        lines += [f"{k}:{v}" for k, v in DEMO_OPTIONS.items() if k not in seen]
        OPTIONS.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"demo world ready at {TARGET_WORLD}; start it with: gradlew runDemoClient")


if __name__ == "__main__":
    main()
