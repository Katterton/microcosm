say TEST_START
forceload add -32 -32 32 32
execute in microcosm:level_1 run forceload add -32 -32 80 32
execute in microcosm:level_2 run forceload add 48 64 96 112
schedule function mctest:place 40t
