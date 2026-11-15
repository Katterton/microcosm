say TEST_START
forceload add -32 -32 32 32
microcosm at 0 64 0 0 0 0 forceload add ~-16 ~-16 ~160 ~160
microcosm at 16 64 16 0 0 0 forceload add ~32 ~32 ~240 ~240
schedule function mctest:place 40t
