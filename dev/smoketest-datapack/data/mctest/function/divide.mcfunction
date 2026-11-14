# Every block is made of microblocks: an arm pointing at plain dirt at (30,64,30); its room is at (512,0,224) in level 1
setblock 30 64 30 minecraft:dirt
setblock 31 64 30 microcosm:manipulator_arm[facing=west]
microcosm act 31 64 30 break 0 3 3 3
schedule function mctest:divide_check 30t
