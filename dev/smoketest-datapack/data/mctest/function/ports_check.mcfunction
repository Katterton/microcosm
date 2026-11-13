execute in microcosm:level_1 if block 133 14 133 minecraft:chest{Items:[{id:"minecraft:diamond"}]} run say PASS items_into_room
execute in microcosm:level_1 unless block 133 14 133 minecraft:chest{Items:[{id:"minecraft:diamond"}]} run say FAIL items_into_room
execute if block 8 63 8 minecraft:chest{Items:[{id:"minecraft:emerald"}]} run say PASS items_out_of_room
execute unless block 8 63 8 minecraft:chest{Items:[{id:"minecraft:emerald"}]} run say FAIL items_out_of_room
execute in microcosm:level_1 if block 142 5 133 minecraft:redstone_lamp[lit=true] run say PASS redstone_into_room
execute in microcosm:level_1 unless block 142 5 133 minecraft:redstone_lamp[lit=true] run say FAIL redstone_into_room
execute if block 7 64 8 minecraft:redstone_lamp[lit=true] run say PASS redstone_out_of_room
execute unless block 7 64 8 minecraft:redstone_lamp[lit=true] run say FAIL redstone_out_of_room
execute in microcosm:level_1 if block 137 15 137 minecraft:air run say PASS second_port_rejected
execute in microcosm:level_1 unless block 137 15 137 minecraft:air run say FAIL second_port_rejected
execute if block 8 64 8 microcosm:cell[powered=false] run say PASS port_side_does_not_trigger_crafting
execute unless block 8 64 8 microcosm:cell[powered=false] run say FAIL port_side_does_not_trigger_crafting
# Switching the redstone source off inside must turn the outside lamp off
execute in microcosm:level_1 run setblock 129 5 136 minecraft:air
setblock 9 64 8 minecraft:air
schedule function mctest:ports_check2 10t
