execute if block 20 64 19 microcosm:cell run say PASS projectile_opens_neighbour
execute unless block 20 64 19 microcosm:cell run say FAIL projectile_opens_neighbour
execute in microcosm:level_1 if entity @e[type=minecraft:ender_pearl,x=352,y=0,z=40,dx=16,dy=16,dz=24] run say INFO pearl_still_flying
execute in microcosm:level_1 if block 360 5 63 minecraft:air run say PASS wall_gone_after_opening
execute in microcosm:level_1 unless block 360 5 63 minecraft:air run say FAIL wall_gone_after_opening
execute if block 20 65 20 microcosm:cell run say PASS fluid_opens_neighbour
execute unless block 20 65 20 microcosm:cell run say FAIL fluid_opens_neighbour
schedule function mctest:walls_check2 300t
