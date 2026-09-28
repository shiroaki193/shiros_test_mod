kill @e[type=mobarmsrace:mortar_creeper]
kill @e[type=mobarmsrace:creeper_shell]
kill @e[type=minecraft:creeper]
kill @e[type=minecraft:villager]
kill @e[type=minecraft:snow_golem]
kill @e[type=minecraft:iron_golem]
kill @e[type=minecraft:cat]
kill @e[type=minecraft:zombie]
scoreboard players add #wave mar_demo 1
execute if score #wave mar_demo matches 6.. run scoreboard players set #wave mar_demo 1
execute if score #wave mar_demo matches 1 run function mobarmsrace_demo:wave_ciws
execute if score #wave mar_demo matches 2 run function mobarmsrace_demo:wave_cat
execute if score #wave mar_demo matches 3 run function mobarmsrace_demo:wave_aim
execute if score #wave mar_demo matches 4 run function mobarmsrace_demo:wave_long
execute if score #wave mar_demo matches 5 run function mobarmsrace_demo:wave_zombie
