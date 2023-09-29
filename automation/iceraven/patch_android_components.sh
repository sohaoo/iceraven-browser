cp automation/iceraven/assets/*.xml android-components/components/feature/search/src/main/assets/searchplugins
cp -f automation/iceraven/assets/list.json android-components/components/feature/search/src/main/assets/search

sed -i '42i\    "startpage",' android-components/components/feature/search/src/main/java/mozilla/components/feature/search/storage/SearchEngineReader.kt
