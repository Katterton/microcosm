# 8 125 8 and 8 126 8 lie in different tiles (126 blocks tall), so their rooms live in different pool dimensions
microcosm at 8 125 8 5 16 5 execute if block ~ ~ ~ microcosm:cell_wall[passable=true] run say PASS tile_border_wall_passable_below
microcosm at 8 125 8 5 16 5 execute unless block ~ ~ ~ microcosm:cell_wall[passable=true] run say FAIL tile_border_wall_passable_below
microcosm at 8 126 8 5 -1 5 execute if block ~ ~ ~ microcosm:cell_wall[passable=true] run say PASS tile_border_wall_passable_above
microcosm at 8 126 8 5 -1 5 execute unless block ~ ~ ~ microcosm:cell_wall[passable=true] run say FAIL tile_border_wall_passable_above
microcosm at 8 125 8 5 -1 5 execute if block ~ ~ ~ microcosm:cell_wall[passable=false] run say PASS inner_wall_solid
microcosm at 8 125 8 5 -1 5 execute unless block ~ ~ ~ microcosm:cell_wall[passable=false] run say FAIL inner_wall_solid
say TEST_DONE
stop
