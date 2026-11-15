microcosm at 16 64 16 0 0 0 execute if block ~67 ~0 ~67 minecraft:air if block ~68 ~0 ~67 minecraft:stone run say PASS arm_place_and_break
microcosm at 16 64 16 0 0 0 execute unless block ~68 ~0 ~67 minecraft:stone run say FAIL arm_place_and_break
execute if block 21 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:stone",count:3,components:{"microcosm:scale":1}},{Slot:1b,id:"minecraft:cobblestone",count:1,components:{"microcosm:scale":1}}]} run say PASS arm_inventory_updated
execute unless block 21 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:stone",count:3,components:{"microcosm:scale":1}},{Slot:1b,id:"minecraft:cobblestone",count:1,components:{"microcosm:scale":1}}]} run say FAIL arm_inventory_updated
microcosm at 16 64 16 64 0 64 microcosm at ~ ~ ~ 0 0 0 execute if block ~129 ~0 ~129 minecraft:gold_block run say PASS probe_places_on_level_2
microcosm at 16 64 16 64 0 64 microcosm at ~ ~ ~ 0 0 0 execute unless block ~129 ~0 ~129 minecraft:gold_block run say FAIL probe_places_on_level_2
execute if block 21 64 20 microcosm:manipulator_arm{Items:[{Slot:2b,id:"minecraft:stone",count:1}]} run microcosm at 16 64 16 0 0 0 execute if block ~70 ~0 ~70 minecraft:air run say PASS normal_block_refused_in_room
execute unless block 21 64 20 microcosm:manipulator_arm{Items:[{Slot:2b,id:"minecraft:stone",count:1}]} run say FAIL normal_block_refused_in_room
execute if block 20 64 21 microcosm:nano_probe{energy:199500} run say PASS probe_used_energy
execute unless block 20 64 21 microcosm:nano_probe{energy:199500} run say FAIL probe_used_energy
microcosm at 16 64 16 64 0 64 microcosm at ~ ~ ~ 0 0 0 execute if block ~130 ~0 ~130 minecraft:air run say PASS arm_cannot_reach_level_2
microcosm at 16 64 16 64 0 64 microcosm at ~ ~ ~ 0 0 0 execute unless block ~130 ~0 ~130 minecraft:air run say FAIL arm_cannot_reach_level_2
execute if block 19 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:diamond_block",count:1}]} run say PASS arm_kept_item
execute unless block 19 64 20 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:diamond_block",count:1}]} run say FAIL arm_kept_item
schedule function mctest:divide 5t
