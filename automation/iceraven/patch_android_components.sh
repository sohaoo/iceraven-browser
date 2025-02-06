#!/usr/bin/env bash

shopt -s globstar

cp automation/iceraven/assets/*.xml android-components/components/feature/search/src/main/assets/searchplugins
cp -f automation/iceraven/assets/list.json android-components/components/feature/search/src/main/assets/search

search_engines=( startpage brave )
for engine in "${search_engines[@]}"
do
  sed -i "41i\    \"$engine\"," android-components/components/feature/search/src/main/java/mozilla/components/feature/search/storage/SearchEngineReader.kt
done

sed -i "s#gleanPythonEnvDir#// gleanPythonEnvDir#g" android-components/**/*.gradle

git -C android-components apply < automation/iceraven/patches/top_sites_no_most_visted_sites.patch
git -C android-components apply < automation/iceraven/patches/suggestions_increase_number.patch