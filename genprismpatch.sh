#!/bin/sh
SJH_VERSION=2.1.24
BSL_VERSION=1.1.2
JJFS_VERSION=0.4.0
ASM_VERSION=9.6
MC_VERSION=1.20.4
PILLOW_LOADER_VERSION=`sed -rn "s#version \= \"(.*)\"#\1#p" build.gradle`
read -rp "Please input PrismLauncher path (default to ~/.local/share/PrismLauncher):" PRISM_PATH
if [ -z $PRISM_PATH ]; then
PRISM_PATH=~/.local/share/PrismLauncher
fi
if [ ! -d $PRISM_PATH/instances ]; then
echo $PRISM_PATH is not a vaild PrismLauncher path!.
exit 1
fi

read -rp "Please input classpath seprator (default to \`:\`):" SEPRATOR
if [ -z $SEPRATOR ]; then
SEPRATOR=:
fi

MAVEN_LOCAL=~/.m2/repository
if [ ! -f $MAVEN_LOCAL/net/pillowmc/intermediary2srg/$MC_VERSION/intermediary2srg-$MC_VERSION.jar ]
then
    echo Generating mappings...
    mkdir genmappings
    cd genmappings
    mkdir -p $MAVEN_LOCAL/net/pillowmc/intermediary2srg/$MC_VERSION
    wget https://pillowmc.github.io/mappinggen/mappinggen-0.1.1.jar
    wget https://maven.fabricmc.net/net/fabricmc/mapping-io/0.5.1/mapping-io-0.5.1.jar
    wget https://maven.fabricmc.net/net/fabricmc/intermediary/$MC_VERSION/intermediary-$MC_VERSION-v2.jar
    wget https://maven.neoforged.net/releases/net/neoforged/installertools/installertools/2.1.2/installertools-2.1.2-fatjar.jar
    unzip intermediary-$MC_VERSION-v2.jar
    java -jar installertools-2.1.2-fatjar.jar --task DOWNLOAD_MOJMAPS --version $MC_VERSION --side client --output ./mojmaps-$MC_VERSION-client.txt
    java -cp mapping-io-0.5.1.jar -jar mappinggen-0.1.1.jar mojmaps-$MC_VERSION-client.txt mappings/mappings.tiny $MAVEN_LOCAL/net/pillowmc/intermediary2srg/$MC_VERSION/intermediary2srg-$MC_VERSION.jar
    cd -
    rm -r genmappings
fi
echo Building...
./gradlew build publishToMavenLocal
echo Done.
cat > net.pillowmc.pillow.json << EOF
{
    "formatVersion": 1,
    "name": "Pillow",
    "uid": "net.pillowmc.pillow",
    "version": "$PILLOW_LOADER_VERSION",
    "minecraftArguments": "--username \${auth_player_name} --version \${version_name} --gameDir \${game_directory} --assetsDir \${assets_root} --assetIndex \${assets_index_name} --uuid \${auth_uuid} --accessToken \${auth_access_token} --userType \${user_type} --versionType \${version_type} --fml.neoForgeVersion 20.4.196 --fml.fmlVersion 2.0.17 --fml.mcVersion $MC_VERSION --fml.neoFormVersion 20231207.154220 --launchTarget pillowclient",
    "+jvmArgs": [
        "-Dfml.pluginLayerLibraries=",
        "-DlibraryDirectory=$PRISM_PATH/libraries/",
        "-DmergeModules=jna-5.10.0.jar,jna-platform-5.10.0.jar",
        "-DignoreList=securejarhandler-$SJH_VERSION.jar,asm-,bootstraplauncher-$BSL_VERSION.jar,JarJarFileSystems-$JJFS_VERSION.jar,intermediary-,client-extra,neoforge-,$MC_VERSION.jar,datafixerupper,minecraft-$MC_VERSION-client.jar",
        "-p",
        "$PRISM_PATH/libraries/cpw/mods/bootstraplauncher/$BSL_VERSION/bootstraplauncher-$BSL_VERSION.jar$SEPRATOR$PRISM_PATH/libraries/cpw/mods/securejarhandler/$SJH_VERSION/securejarhandler-$SJH_VERSION.jar$SEPRATOR$PRISM_PATH/libraries/org/ow2/asm/asm-commons/$ASM_VERSION/asm-commons-$ASM_VERSION.jar$SEPRATOR$PRISM_PATH/libraries/org/ow2/asm/asm-util/$ASM_VERSION/asm-util-$ASM_VERSION.jar$SEPRATOR$PRISM_PATH/libraries/org/ow2/asm/asm-analysis/$ASM_VERSION/asm-analysis-$ASM_VERSION.jar$SEPRATOR$PRISM_PATH/libraries/org/ow2/asm/asm-tree/$ASM_VERSION/asm-tree-$ASM_VERSION.jar$SEPRATOR$PRISM_PATH/libraries/org/ow2/asm/asm/$ASM_VERSION/asm-$ASM_VERSION.jar",
        "--add-modules",
        "ALL-MODULE-PATH",
        "--add-opens","java.base/java.util.jar=cpw.mods.securejarhandler",
        "--add-opens","java.base/java.lang.invoke=cpw.mods.securejarhandler",
        "--add-exports","java.base/sun.security.util=cpw.mods.securejarhandler",
        "--add-exports","jdk.naming.dns/com.sun.jndi.dns=java.naming"
    ],
    "libraries": [
        {
            "name": "net.pillowmc:pillow:$PILLOW_LOADER_VERSION",
            "url": "file://$MAVEN_LOCAL"
        },
        {
            "name": "net.pillowmc:intermediary2srg:$MC_VERSION",
            "url": "file://$MAVEN_LOCAL"
        }
    ],
    "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher"
}
EOF
