X1=1
Y1=1
X2=749
Y2=499
#X1=200
#Y1=200
#X2=250
#Y2=220
Z=22
for x in `seq $X1 $X2`; do
for y in `seq $Y1 $Y2`; do

if ! [ -f ${Z}/${y}_${x}.jpg ]; then
echo "Getting ${x},${y}"
curl --referer http://map.schweizmobil.ch/ -s http://wmts8.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/20151231/21781/${Z}/${y}/${x}.jpeg -o ${Z}/${y}_${x}.jpg  &
sleep 0.13
fi
done
wait
done
montage -mode concatenate -tile "$((X2-X1+1))x" "${Z}/*.jpg" out_z${Z}_${X1}_${Y1}-${X2}_${Y2}.png
