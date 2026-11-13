# Port tests on a fresh cell at (8,64,8); its room is at (128,0,128) in level 1
setblock 8 64 8 microcosm:cell
# Items in: hopper on top of the cell -> UP port -> chest behind it inside the room
execute in microcosm:level_1 run setblock 133 14 133 minecraft:chest
execute in microcosm:level_1 run setblock 133 15 133 microcosm:port[facing=up]
setblock 8 65 8 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:diamond",count:3}]}
# Items out: hopper inside the room -> DOWN port -> chest below the cell
setblock 8 63 8 minecraft:chest
execute in microcosm:level_1 run setblock 136 0 136 microcosm:port[facing=down]
execute in microcosm:level_1 run setblock 136 1 136 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:emerald",count:2}]}
# Redstone in: redstone block east of the cell -> EAST port (input) -> lamp behind it
execute in microcosm:level_1 run setblock 142 5 133 minecraft:redstone_lamp
execute in microcosm:level_1 run setblock 143 5 133 microcosm:port[facing=east]
setblock 9 64 8 minecraft:redstone_block
# Redstone out: redstone block inside -> WEST port (output) -> lamp west of the cell
execute in microcosm:level_1 run setblock 129 5 136 minecraft:redstone_block
execute in microcosm:level_1 run setblock 128 5 136 microcosm:port[facing=west,mode=output]
setblock 7 64 8 minecraft:redstone_lamp
# A second port on the same side pops off
execute in microcosm:level_1 run setblock 137 15 137 microcosm:port[facing=up]
# A port in the middle of the room is not linked and must not crash anything
execute in microcosm:level_1 run setblock 135 7 135 microcosm:port[facing=north]
schedule function mctest:ports_check 60t
