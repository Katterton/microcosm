execute if block 34 64 30 microcosm:scale_converter{pending_count:4032,Items:[{Slot:1b,id:"minecraft:gold_block",count:64,components:{"microcosm:scale":1}}]} run say PASS converter_shrinks_1_to_4096
execute unless block 34 64 30 microcosm:scale_converter{pending_count:4032,Items:[{Slot:1b,id:"minecraft:gold_block",count:64,components:{"microcosm:scale":1}}]} run say FAIL converter_shrinks_1_to_4096
execute if block 36 64 30 microcosm:scale_converter{collected_count:64} run say PASS converter_collects_for_growing
execute unless block 36 64 30 microcosm:scale_converter{collected_count:64} run say FAIL converter_collects_for_growing
say TEST_DONE
stop
