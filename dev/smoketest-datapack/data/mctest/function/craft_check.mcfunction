execute if block 40 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say PASS foreign_scale_not_craftable
execute unless block 40 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say FAIL foreign_scale_not_craftable
execute unless block 44 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say PASS normal_ingots_still_craft
execute if block 44 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say FAIL normal_ingots_still_craft
# Walls open on demand: an ender pearl flies north out of the room of (20,64,20); behind the wall is air at (20,64,19)
microcosm at 16 64 16 0 0 0 summon minecraft:ender_pearl ~72.5 ~5.5 ~66.5 {Motion:[0.0,0.0,-1.2]}
# Water against the top wall opens the air block above (20,65,20)
microcosm at 16 64 16 0 0 0 setblock ~72 ~15 ~78 minecraft:water
schedule function mctest:walls_check 30t
