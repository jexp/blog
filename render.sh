file=$1
file=${file%%.txt}
file=${file%%.adoc}
file=${file##adoc/}
echo $file
asciidoctor $1 -a allow-uri-read -a img=../img -D html/
