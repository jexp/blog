file=$1
file=${file%%.txt}
file=${file%%.adoc}
file=${file##adoc/}
echo $file
asciidoctor $1 -a img=../img -D html/
open -a Camino html/$file.html
