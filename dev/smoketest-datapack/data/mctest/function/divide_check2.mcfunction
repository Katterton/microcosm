execute in microcosm:level_1 if block 515 3 227 minecraft:air run say INFO room_cleared
execute if block 30 64 30 minecraft:dirt run say PASS uniform_room_collapses
execute unless block 30 64 30 minecraft:dirt run say FAIL uniform_room_collapses
# Scale converter: one gold block becomes 4096 Milliblock gold blocks, handed out 64 at a time
setblock 34 64 30 microcosm:scale_converter{Items:[{Slot:0b,id:"minecraft:gold_block",count:1}]}
setblock 36 64 30 microcosm:scale_converter[mode=grow]{Items:[{Slot:0b,id:"minecraft:iron_block",count:64,components:{"microcosm:scale":1}}]}
schedule function mctest:converter_check 10t
