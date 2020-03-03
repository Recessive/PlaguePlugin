echo Making jar
./gradlew jar
echo Copying
cp ./build/libs/* ~/Documents/mindustry/game/server/mindustry-server/config/mods

