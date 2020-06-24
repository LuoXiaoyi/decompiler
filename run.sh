echo "begin to decomplie dir: $@"
HOME=`dirname $0`
echo "Home: $HOME"
java -cp $HOME:$HOME/cfr-0.139.jar:$HOME/perfma-decompiler.jar com.perfma.Main $@
