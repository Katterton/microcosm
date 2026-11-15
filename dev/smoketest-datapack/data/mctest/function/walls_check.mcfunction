execute if block 20 64 19 microcosm:cell run say PASS projectile_opens_neighbour
execute unless block 20 64 19 microcosm:cell run say FAIL projectile_opens_neighbour
microcosm at 16 64 16 0 0 0 execute if block ~72 ~5 ~63 minecraft:air run say PASS wall_gone_after_opening
microcosm at 16 64 16 0 0 0 execute unless block ~72 ~5 ~63 minecraft:air run say FAIL wall_gone_after_opening
execute if block 20 65 20 microcosm:cell run say PASS fluid_opens_neighbour
execute unless block 20 65 20 microcosm:cell run say FAIL fluid_opens_neighbour
schedule function mctest:walls_check2 300t
