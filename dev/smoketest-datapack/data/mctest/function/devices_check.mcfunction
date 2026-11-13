execute in microcosm:level_1 if block 355 0 67 minecraft:air if block 356 0 67 minecraft:stone run say PASS arm_place_and_break
execute in microcosm:level_1 unless block 356 0 67 minecraft:stone run say FAIL arm_place_and_break
execute if block 21 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:stone",count:3},{Slot:1b,id:"minecraft:cobblestone",count:1}]} run say PASS arm_inventory_updated
execute unless block 21 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:stone",count:3},{Slot:1b,id:"minecraft:cobblestone",count:1}]} run say FAIL arm_inventory_updated
execute in microcosm:level_2 if block 417 0 129 minecraft:gold_block run say PASS probe_places_on_level_2
execute in microcosm:level_2 unless block 417 0 129 minecraft:gold_block run say FAIL probe_places_on_level_2
execute if block 20 64 21 microcosm:nano_probe{energy:199500} run say PASS probe_used_energy
execute unless block 20 64 21 microcosm:nano_probe{energy:199500} run say FAIL probe_used_energy
execute in microcosm:level_2 if block 418 0 130 minecraft:air run say PASS arm_cannot_reach_level_2
execute in microcosm:level_2 unless block 418 0 130 minecraft:air run say FAIL arm_cannot_reach_level_2
execute if block 19 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:diamond_block",count:1}]} run say PASS arm_kept_item
execute unless block 19 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:diamond_block",count:1}]} run say FAIL arm_kept_item
say TEST_DONE
stop
