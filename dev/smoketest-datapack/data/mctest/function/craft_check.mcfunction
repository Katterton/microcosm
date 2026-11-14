execute if block 40 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say PASS foreign_scale_not_craftable
execute unless block 40 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say FAIL foreign_scale_not_craftable
execute unless block 44 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say PASS normal_ingots_still_craft
execute if block 44 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot"}]} run say FAIL normal_ingots_still_craft
say TEST_DONE
stop
