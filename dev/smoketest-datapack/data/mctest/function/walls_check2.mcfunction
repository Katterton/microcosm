execute if block 20 64 19 minecraft:air run say PASS empty_room_collapses_to_air
execute unless block 20 64 19 minecraft:air run say FAIL empty_room_collapses_to_air
microcosm at 16 64 16 0 0 0 execute if block ~72 ~5 ~63 microcosm:cell_wall run say PASS wall_back_after_collapse
microcosm at 16 64 16 0 0 0 execute unless block ~72 ~5 ~63 microcosm:cell_wall run say FAIL wall_back_after_collapse
setblock 8 125 8 microcosm:cell
setblock 8 126 8 microcosm:cell
schedule function mctest:tiles_check 20t
