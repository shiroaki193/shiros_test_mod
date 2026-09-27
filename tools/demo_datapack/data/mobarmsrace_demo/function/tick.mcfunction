execute as @a[tag=!mar_ready] run function mobarmsrace_demo:viewer
execute if entity @a run scoreboard players add #t mar_demo 1
execute if score #t mar_demo matches 40 run function mobarmsrace_demo:wave
execute if score #t mar_demo matches 400.. run scoreboard players set #t mar_demo 0
