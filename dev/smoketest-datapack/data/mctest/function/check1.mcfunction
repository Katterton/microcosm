# Walls around the two neighbouring rooms A (0,0,0) and B (16,0,0)
execute in microcosm:level_1 if block 5 -1 5 microcosm:cell_wall run say PASS wall_below_A
execute in microcosm:level_1 unless block 5 -1 5 microcosm:cell_wall run say FAIL wall_below_A
execute in microcosm:level_1 if block -1 5 5 microcosm:cell_wall run say PASS wall_west_A
execute in microcosm:level_1 unless block -1 5 5 microcosm:cell_wall run say FAIL wall_west_A
execute in microcosm:level_1 if block 16 5 5 minecraft:air if block 15 5 5 minecraft:air run say PASS open_between_A_B
execute in microcosm:level_1 unless block 16 5 5 minecraft:air run say FAIL open_between_A_B
execute in microcosm:level_1 if block 32 5 5 microcosm:cell_wall run say PASS wall_east_B
execute in microcosm:level_1 unless block 32 5 5 microcosm:cell_wall run say FAIL wall_east_B
execute in microcosm:level_1 if block 5 16 5 microcosm:cell_wall run say PASS wall_top_A
execute in microcosm:level_1 unless block 5 16 5 microcosm:cell_wall run say FAIL wall_top_A
# Nested cell inside room B has a room in level 2 at (64,80,80)
execute in microcosm:level_2 if block 70 79 90 microcosm:cell_wall run say PASS nested_room_walls
execute in microcosm:level_2 unless block 70 79 90 microcosm:cell_wall run say FAIL nested_room_walls
# Build the crafter_core cube recipe in room A, without the redstone core first
execute in microcosm:level_1 run fill 0 0 0 15 0 15 minecraft:iron_block
microcosm check 0 64 0
execute if block 0 64 0 microcosm:cell run say PASS incomplete_cube_not_crafted
execute unless block 0 64 0 microcosm:cell run say FAIL incomplete_cube_not_crafted
execute in microcosm:level_1 run fill 7 1 7 8 2 8 minecraft:redstone_block
execute in microcosm:level_1 run setblock 2 3 2 minecraft:chest
# Trigger with redstone instead of the command
setblock 0 65 0 minecraft:redstone_block
schedule function mctest:check2 10t
