#!/bin/bash
# renders one Short to MP4: frames from the local build, sound from the finished mix
SP="$(cd "$(dirname "$0")" && pwd)"
OUT="$SP/../videos"
mkdir -p $OUT
one() {
  local lang=$1 k=$2 id=$3 name=$4
  local dir=$SP/vid/fr_${lang}_$id
  cd $SP && node export_short.js $k $lang $dir 30 1000 frames > $SP/vid/log_${lang}_$id.txt 2>&1
  ffmpeg -v error -y -framerate 30 -i $dir/f%05d.jpg -i $SP/vid/mix/${lang}_$id.wav -c:v libx264 -preset medium -crf 19 -pix_fmt yuv420p -r 30 \
    -af loudnorm=I=-14:TP=-1.5:LRA=11 -c:a aac -b:a 192k -ar 48000 -shortest -movflags +faststart $OUT/$name.mp4 >> $SP/vid/log_${lang}_$id.txt 2>&1
  echo "done $name $(du -h $OUT/$name.mp4 | cut -f1)"
}
(one en 0 piloto en-pilot-vs-pilot; one en 2 papa en-space-dad; one es 1 dolar es-un-dolar) &
(one en 1 dolar en-one-dollar; one es 0 piloto es-piloto-contra-piloto; one es 2 papa es-papa-en-el-espacio) &
wait
echo ALL_DONE
