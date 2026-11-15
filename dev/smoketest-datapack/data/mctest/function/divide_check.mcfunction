execute if block 30 64 30 microcosm:cell run say PASS block_divided_on_first_change
execute unless block 30 64 30 microcosm:cell run say FAIL block_divided_on_first_change
microcosm at 16 64 16 0 0 0 execute if block ~227 ~3 ~227 minecraft:air if block ~228 ~3 ~227 minecraft:dirt if block ~224 ~0 ~224 minecraft:dirt if block ~239 ~15 ~239 minecraft:dirt run say PASS room_filled_with_material
microcosm at 16 64 16 0 0 0 execute unless block ~228 ~3 ~227 minecraft:dirt run say FAIL room_filled_with_material
execute if block 31 64 30 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:dirt",count:1}]} run say PASS microblock_mined
execute unless block 31 64 30 microcosm:manipulator_arm{Items:[{Slot:0b,id:"minecraft:dirt",count:1}]} run say FAIL microblock_mined
# Put it back: the room is uniform again and collapses after the grace period
microcosm act 31 64 30 place 0 3 3 3
schedule function mctest:divide_check2 260t
