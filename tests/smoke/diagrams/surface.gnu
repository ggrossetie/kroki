# set terminal svg size 600,400 dynamic enhanced font 'arial,10' mousing name "surface1_1" butt dashlength 1.0
# set output 'surface1.1.svg'
set grid nopolar
set grid xtics nomxtics ytics nomytics noztics nomztics nortics nomrtics \
 nox2tics nomx2tics noy2tics nomy2tics nocbtics nomcbtics
set grid layerdefault   lt 0 linecolor 0 linewidth 0.500,  lt 0 linecolor 0 linewidth 0.500
set style increment default
set hidden3d back offset 1 trianglepattern 3 undefined 1 altdiagonal bentover
set style data lines
set title "3D surface from a grid (matrix) of Z values"
set xrange [ -0.500000 : 4.50000 ] noreverse nowriteback
set x2range [ * : * ] noreverse writeback
set yrange [ -0.500000 : 4.50000 ] noreverse nowriteback
set y2range [ * : * ] noreverse writeback
set zrange [ * : * ] noreverse writeback
set cbrange [ * : * ] noreverse writeback
set rrange [ * : * ] noreverse writeback
## Last datafile plotted: "$grid"
splot '$grid' matrix with lines notitle
