kill @e[type=mobarmsrace:mortar_creeper]
kill @e[type=mobarmsrace:creeper_shell]
kill @e[type=minecraft:creeper]
kill @e[type=minecraft:villager]
kill @e[type=minecraft:snow_golem]
kill @e[type=minecraft:iron_golem]
kill @e[type=minecraft:cat]
scoreboard players add #wave mar_demo 1
execute if score #wave mar_demo matches 4.. run scoreboard players set #wave mar_demo 1
execute if score #wave mar_demo matches 1 run function mobarmsrace_demo:wave_ciws
execute if score #wave mar_demo matches 2 run function mobarmsrace_demo:wave_cat
execute if score #wave mar_demo matches 3 run function mobarmsrace_demo:wave_aim
