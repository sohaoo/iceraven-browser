#!/usr/bin/env bash

#cp automation/iceraven/assets/*.xml android-components/components/feature/search/src/main/assets/searchplugins
#cp -f automation/iceraven/assets/list.json android-components/components/feature/search/src/main/assets/search

search_engines=( startpage brave )
for engine in "${search_engines[@]}"
do
  sed -i "42i\    \"$engine\"," android-components/components/feature/search/src/main/java/mozilla/components/feature/search/storage/SearchEngineReader.kt
done
