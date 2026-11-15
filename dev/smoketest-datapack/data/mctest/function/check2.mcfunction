execute if block 0 64 0 minecraft:crafter run say PASS cube_crafted_by_redstone
execute unless block 0 64 0 minecraft:crafter run say FAIL cube_crafted_by_redstone
microcosm at 0 64 0 0 0 0 execute if block ~3 ~0 ~3 minecraft:air if block ~7 ~1 ~7 minecraft:air run say PASS cube_consumed
microcosm at 0 64 0 0 0 0 execute unless block ~3 ~0 ~3 minecraft:air run say FAIL cube_consumed
microcosm at 0 64 0 0 0 0 execute if block ~2 ~3 ~2 minecraft:air run say PASS leftover_removed
microcosm at 0 64 0 0 0 0 execute unless block ~2 ~3 ~2 minecraft:air run say FAIL leftover_removed
execute if entity @e[type=item,nbt={Item:{id:"minecraft:chest"}}] run say PASS leftover_dropped_outside
execute unless entity @e[type=item,nbt={Item:{id:"minecraft:chest"}}] run say FAIL leftover_dropped_outside
microcosm at 0 64 0 0 0 0 execute if block ~15 ~5 ~5 microcosm:cell_wall run say PASS wall_closed_after_cell_removed
microcosm at 0 64 0 0 0 0 execute unless block ~15 ~5 ~5 microcosm:cell_wall run say FAIL wall_closed_after_cell_removed
# Structure recipe (beacon_core) in room C at (48,0,0); extra blocks in "anything" cells are allowed
microcosm at 0 64 0 0 0 0 fill ~54 ~6 ~6 ~57 ~9 ~9 minecraft:glass
microcosm at 0 64 0 0 0 0 fill ~55 ~7 ~7 ~56 ~8 ~8 minecraft:sea_lantern
microcosm at 0 64 0 0 0 0 setblock ~50 ~1 ~50 minecraft:stone
microcosm at 0 64 0 0 0 0 setblock ~49 ~2 ~1 minecraft:stone
microcosm check 3 64 0
execute if block 3 64 0 minecraft:beacon run say PASS structure_cube_crafted
execute unless block 3 64 0 minecraft:beacon run say FAIL structure_cube_crafted
schedule function mctest:ports 5t
