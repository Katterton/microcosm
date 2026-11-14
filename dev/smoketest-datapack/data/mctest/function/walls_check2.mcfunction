execute if block 20 64 19 minecraft:air run say PASS empty_room_collapses_to_air
execute unless block 20 64 19 minecraft:air run say FAIL empty_room_collapses_to_air
execute in microcosm:level_1 if block 360 5 63 microcosm:cell_wall run say PASS wall_back_after_collapse
execute in microcosm:level_1 unless block 360 5 63 microcosm:cell_wall run say FAIL wall_back_after_collapse
say TEST_DONE
stop
