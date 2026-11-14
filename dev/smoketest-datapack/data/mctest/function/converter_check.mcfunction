execute if block 34 64 30 microcosm:scale_converter{pending_count:4032,Items:[{Slot:1b,id:"minecraft:gold_block",count:64,components:{"microcosm:scale":1}}]} run say PASS converter_shrinks_1_to_4096
execute unless block 34 64 30 microcosm:scale_converter{pending_count:4032,Items:[{Slot:1b,id:"minecraft:gold_block",count:64,components:{"microcosm:scale":1}}]} run say FAIL converter_shrinks_1_to_4096
execute if block 36 64 30 microcosm:scale_converter{collected_count:64} run say PASS converter_collects_for_growing
execute unless block 36 64 30 microcosm:scale_converter{collected_count:64} run say FAIL converter_collects_for_growing
microcosm selftest
# Material of a foreign scale is no ingredient: nine Milliblock gold ingots must not craft a gold block
setblock 40 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:1b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:2b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:3b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:4b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:5b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:6b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:7b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}},{Slot:8b,id:"minecraft:gold_ingot",count:1,components:{"microcosm:scale":1}}]}
setblock 44 64 30 minecraft:crafter{Items:[{Slot:0b,id:"minecraft:gold_ingot",count:1},{Slot:1b,id:"minecraft:gold_ingot",count:1},{Slot:2b,id:"minecraft:gold_ingot",count:1},{Slot:3b,id:"minecraft:gold_ingot",count:1},{Slot:4b,id:"minecraft:gold_ingot",count:1},{Slot:5b,id:"minecraft:gold_ingot",count:1},{Slot:6b,id:"minecraft:gold_ingot",count:1},{Slot:7b,id:"minecraft:gold_ingot",count:1},{Slot:8b,id:"minecraft:gold_ingot",count:1}]}
setblock 40 65 30 minecraft:redstone_block
setblock 44 65 30 minecraft:redstone_block
schedule function mctest:craft_check 20t
