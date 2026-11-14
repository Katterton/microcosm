# Devices around a cell at (20,64,20); its room is at (352,0,64) in level 1
setblock 20 64 20 microcosm:cell
setblock 21 64 20 microcosm:manipulator_arm[facing=west]{Items:[{Slot:0b,id:"minecraft:stone",count:5,components:{"microcosm:scale":1}},{Slot:2b,id:"minecraft:stone",count:1}]}
microcosm act 21 64 20 place 0 3 0 3
microcosm act 21 64 20 place 0 4 0 3
microcosm act 21 64 20 break 0 3 0 3
# A normal stone block (slot 2) is 16 times too big for the room and must be refused
microcosm act 21 64 20 place 2 6 0 6
# Nested cell inside that room at local (8,0,8); its room is at (416,0,128) in level 2
execute in microcosm:level_1 run setblock 360 0 72 microcosm:cell
setblock 20 64 21 microcosm:nano_probe[facing=north]{energy:200000,Items:[{Slot:0b,id:"minecraft:gold_block",count:2,components:{"microcosm:scale":2}}]}
microcosm act 20 64 21 place 0 1 0 1 8 0 8
# The tier 1 arm must not reach level 2
setblock 19 64 20 microcosm:manipulator_arm[facing=east]{Items:[{Slot:0b,id:"minecraft:diamond_block",count:1,components:{"microcosm:scale":2}}]}
microcosm act 19 64 20 place 0 2 0 2 8 0 8
schedule function mctest:devices_check 80t
