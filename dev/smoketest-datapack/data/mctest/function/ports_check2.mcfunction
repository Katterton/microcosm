execute if block 7 64 8 minecraft:redstone_lamp[lit=false] run say PASS redstone_out_turns_off
execute unless block 7 64 8 minecraft:redstone_lamp[lit=false] run say FAIL redstone_out_turns_off
execute in microcosm:level_1 if block 142 5 133 minecraft:redstone_lamp[lit=false] run say PASS redstone_in_turns_off
execute in microcosm:level_1 unless block 142 5 133 minecraft:redstone_lamp[lit=false] run say FAIL redstone_in_turns_off
schedule function mctest:devices 5t
