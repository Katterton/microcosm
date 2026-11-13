setblock 0 64 0 microcosm:cell
setblock 1 64 0 microcosm:cell
setblock 3 64 0 microcosm:cell
execute in microcosm:level_1 run setblock 20 5 5 microcosm:cell
schedule function mctest:check1 40t
