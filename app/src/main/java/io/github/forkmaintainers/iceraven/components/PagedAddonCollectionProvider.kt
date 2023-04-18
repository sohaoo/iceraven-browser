/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package io.github.forkmaintainers.iceraven.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.annotation.VisibleForTesting
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonsProvider
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.kotlin.sanitizeFileName
import mozilla.components.support.ktx.kotlin.sanitizeURL
import mozilla.components.support.ktx.util.readAndDeserialize
import mozilla.components.support.ktx.util.writeString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.fenix.Config
import org.mozilla.fenix.ext.settings
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal const val API_VERSION = "api/v4"
internal const val DEFAULT_SERVER_URL = "https://addons.mozilla.org"
internal const val COLLECTION_FILE_NAME_PREFIX = "%s_components_addon_collection"
internal const val COLLECTION_FILE_NAME = "%s_components_addon_collection_%s.json"
internal const val COLLECTION_FILE_NAME_WITH_LANGUAGE = "%s_components_addon_collection_%s_%s.json"
internal const val REGEX_FILE_NAMES = "%s_components_addon_collection(_\\w+)?_%s.json"
internal const val MINUTE_IN_MS = 60 * 1000
internal const val DEFAULT_READ_TIMEOUT_IN_SECONDS = 20L

/**
 * Provide access to the AMO collections API.
 * https://addons-server.readthedocs.io/en/latest/topics/api/collections.html
 *
 * Unlike the android-components version, supports multiple-page responses and
 * custom collection accounts.
 *
 * Needs to extend AddonsProvider because AddonsManagerAdapter won't
 * take just any AddonsProvider.
 *
 * @property context A reference to the application context.
 * @property client A [Client] for interacting with the AMO HTTP api.
 * @property serverURL The url of the endpoint to interact with e.g production, staging
 * or testing. Defaults to [DEFAULT_SERVER_URL].
 * @property maxCacheAgeInMinutes maximum time (in minutes) the collection cache
 * should remain valid before a refresh is attempted. Defaults to -1, meaning no
 * cache is being used by default
 */
@Suppress("LongParameterList")
class PagedAddonCollectionProvider(
    private val context: Context,
    private val client: Client,
    private val serverURL: String = DEFAULT_SERVER_URL,
    private val maxCacheAgeInMinutes: Long = -1,
) : AddonsProvider {

    private val logger = Logger("PagedAddonCollectionProvider")

    private val diskCacheLock = Any()

    /**
     * Get the account we should be fetching addons from.
     */
    private fun getCollectionAccount(): String {
        var result = context.settings().customAddonsAccount
        if (Config.channel.isNightlyOrDebug && context.settings()
                .amoCollectionOverrideConfigured()
        ) {
            result = context.settings().overrideAmoUser
        }

        logger.info("Determined collection account: $result")
        return result
    }

    /**
     * Get the collection name we should be fetching addons from.
     */
    private fun getCollectionName(): String {
        var result = context.settings().customAddonsCollection
        if (Config.channel.isNightlyOrDebug && context.settings()
                .amoCollectionOverrideConfigured()
        ) {
            result = context.settings().overrideAmoCollection
        }

        logger.info("Determined collection name: $result")
        return result
    }

    /**
     * Interacts with the collections endpoint to provide a list of available
     * add-ons. May return a cached response, if [allowCache] is true, and the
     * cache is not expired (see [maxCacheAgeInMinutes]) or fetching from
     * AMO failed.
     *
     * @param allowCache whether or not the result may be provided
     * from a previously cached response, defaults to true. Note that
     * [maxCacheAgeInMinutes] must be set for the cache to be active.
     * @param readTimeoutInSeconds optional timeout in seconds to use when fetching
     * available add-ons from a remote endpoint. If not specified [DEFAULT_READ_TIMEOUT_IN_SECONDS]
     * will be used.
     * @param language indicates in which language the translatable fields should be in, if no
     * matching language is found then a fallback translation is returned using the default language.
     * When it is null all translations available will be returned.
     * @throws IOException if the request failed, or could not be executed due to cancellation,
     * a connectivity problem or a timeout.
     */
    @Throws(IOException::class)
    override suspend fun getAvailableAddons(
        allowCache: Boolean,
        readTimeoutInSeconds: Long?,
        language: String?,
    ): List<Addon> {
        // We want to make sure we always use useFallbackFile = false here, as it warranties
        // that we are trying to fetch the latest localized add-ons when the user changes
        // language from the previous one.
        val cachedAvailableAddons =
            if (allowCache && !cacheExpired(context, language, useFallbackFile = false)) {
                readFromDiskCache(language, useFallbackFile = false)
            } else {
                null
            }

        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()

        if (cachedAvailableAddons != null) {
            logger.info("Providing cached list of addons for $collectionAccount collection $collectionName")
            return cachedAvailableAddons
        } else {
            logger.info("Fetching fresh list of addons for $collectionAccount collection $collectionName")
            val langParam = if (!language.isNullOrEmpty()) {
                "?lang=$language"
            } else {
                ""
            }
            return getAllPages(
                listOf(
                    serverURL,
                    API_VERSION,
                    "accounts/account",
                    collectionAccount,
                    "collections",
                    collectionName,
                    "addons",
                    langParam
                ).joinToString("/"),
                readTimeoutInSeconds ?: DEFAULT_READ_TIMEOUT_IN_SECONDS,
            ).also {
                // Cache the JSON object before we parse out the addons
                if (maxCacheAgeInMinutes > 0) {
                    writeToDiskCache(it.toString(), language)
                }
                deleteUnusedCacheFiles(language)
            }.getAddons(language)
        }
    }

    /**
     * Fetches all pages of add-ons from the given URL (following the "next"
     * field in the returned JSON) and combines the "results" arrays into that
     * of the first page. Returns that coalesced object.
     *
     * @param url URL of the first page to fetch
     * @param readTimeoutInSeconds timeout in seconds to use when fetching each page.
     * @throws IOException if the request failed, or could not be executed due to cancellation,
     * a connectivity problem or a timeout.
     */
    @Throws(IOException::class)
    fun getAllPages(url: String, readTimeoutInSeconds: Long): JSONObject {
        // Fetch and compile all the pages into one object we can return
        var compiledResponse: JSONObject? = null
        // Each page tells us where to get the next page, if there is one
        var nextURL: String? = url
        logger.debug("Fetching URI: $nextURL")
        while (nextURL != null) {
            client.fetch(
                Request(
                    url = nextURL,
                    readTimeout = Pair(readTimeoutInSeconds, TimeUnit.SECONDS),
                ),
            )
                .use { response ->
                    if (!response.isSuccess) {
                        val errorMessage =
                            "Failed to fetch addon collection. Status code: ${response.status}"
                        logger.error(errorMessage)
                        throw IOException(errorMessage)
                    }

                    val currentResponse = try {
                        JSONObject(response.body.string(Charsets.UTF_8))
                    } catch (e: JSONException) {
                        throw IOException(e)
                    }
                    if (compiledResponse == null) {
                        compiledResponse = currentResponse
                    } else {
                        // Write the addons into the first response
                        compiledResponse!!.getJSONArray("results")
                            .concat(currentResponse.getJSONArray("results"))
                    }
                    nextURL =
                        if (currentResponse.isNull("next")) null else currentResponse.getString("next")
                }
        }
        return compiledResponse!!
    }

    /**
     * Fetches given Addon icon from the url and returns a decoded Bitmap
     * @throws IOException if the request could not be executed due to cancellation,
     * a connectivity problem or a timeout.
     */
    @Throws(IOException::class)
    suspend fun getAddonIconBitmap(addon: Addon): Bitmap? {
        var bitmap: Bitmap? = null
        if (addon.iconUrl != "") {
            client.fetch(
                Request(url = addon.iconUrl.sanitizeURL()),
            ).use { response ->
                if (response.isSuccess) {
                    response.body.useStream {
                        bitmap = BitmapFactory.decodeStream(it)
                    }
                }
            }
        }

        return bitmap
    }

    @VisibleForTesting
    internal fun writeToDiskCache(collectionResponse: String, language: String?) {
        logger.info("Storing cache file")
        synchronized(diskCacheLock) {
            getCacheFile(
                context,
                language,
                useFallbackFile = false
            ).writeString { collectionResponse }
        }
    }

    @VisibleForTesting
    internal fun readFromDiskCache(language: String?, useFallbackFile: Boolean): List<Addon>? {
        logger.info("Loading cache file")
        synchronized(diskCacheLock) {
            return getCacheFile(context, language, useFallbackFile).readAndDeserialize {
                JSONObject(it).getAddons(language)
            }
        }
    }

    /**
     * Deletes cache files from previous (now unused) collections.
     */
    @VisibleForTesting
    internal fun deleteUnusedCacheFiles(language: String?) {
        val currentCacheFileName = getBaseCacheFile(context, language, useFallbackFile = true).name

        context.filesDir
            .listFiles { _, s ->
                s.startsWith(COLLECTION_FILE_NAME_PREFIX.format(getCollectionAccount())) && s != currentCacheFileName
            }
            ?.forEach {
                logger.debug("Deleting unused collection cache: " + it.name)
                it.delete()
            }
    }

    @VisibleForTesting
    internal fun cacheExpired(
        context: Context,
        language: String?,
        useFallbackFile: Boolean
    ): Boolean {
        return getCacheLastUpdated(
            context,
            language,
            useFallbackFile,
        ) < Date().time - maxCacheAgeInMinutes * MINUTE_IN_MS
    }

    @VisibleForTesting
    internal fun getCacheLastUpdated(
        context: Context,
        language: String?,
        useFallbackFile: Boolean
    ): Long {
        val file = getBaseCacheFile(context, language, useFallbackFile)
        return if (file.exists()) file.lastModified() else -1
    }

    private fun getCacheFile(
        context: Context,
        language: String?,
        useFallbackFile: Boolean
    ): AtomicFile {
        return AtomicFile(getBaseCacheFile(context, language, useFallbackFile))
    }

    @VisibleForTesting
    internal fun getBaseCacheFile(
        context: Context,
        language: String?,
        useFallbackFile: Boolean
    ): File {
        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()
        var file = File(context.filesDir, getCacheFileName(language))
        if (!file.exists() && useFallbackFile) {
            // In situations, where users change languages and we can't retrieve the new one,
            // we always want to fallback to the previous localized file.
            // Try to find first available localized file.
            val regex = Regex(REGEX_FILE_NAMES.format(collectionAccount, collectionName))
            val fallbackFile = context.filesDir.listFiles()?.find { it.name.matches(regex) }

            if (fallbackFile?.exists() == true) {
                file = fallbackFile
            }
        }
        return file
    }

    @VisibleForTesting
    internal fun getCacheFileName(language: String? = ""): String {
        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()

        val fileName = if (language.isNullOrEmpty()) {
            COLLECTION_FILE_NAME.format(collectionAccount, collectionName)
        } else {
            COLLECTION_FILE_NAME_WITH_LANGUAGE.format(collectionAccount, language, collectionName)
        }
        return fileName.sanitizeFileName()
    }

    fun deleteCacheFile() {
        logger.info("Clearing cache file")
        synchronized(diskCacheLock) {
            //val file = getBaseCacheFile(context, language, useFallbackFile = true)
            //return if (file.exists()) file.delete() else false
            context.filesDir.listFiles { _, s ->
                s.contains("components_addon_collection")
            }?.forEach {
                logger.debug("Deleting collection files ${it.name}")
                it.delete()
            }
        }
    }
}

internal fun JSONObject.getAddons(language: String? = null): List<Addon> {
    val addonsJson = getJSONArray("results")
    return (0 until addonsJson.length()).map { index ->
        addonsJson.getJSONObject(index).toAddons(language)
    }
}

internal fun JSONObject.toAddons(language: String? = null): Addon {
    return with(getJSONObject("addon")) {
        val download = getDownload()
        val safeLanguage = language?.lowercase(Locale.getDefault())
        val summary = getSafeTranslations("summary", safeLanguage)
        val isLanguageInTranslations = summary.containsKey(safeLanguage)
        Addon(
            id = getSafeString("guid"),
            authors = getAuthors(),
            categories = getCategories(),
            createdAt = getSafeString("created"),
            updatedAt = getSafeString("last_updated"),
            downloadId = download?.getDownloadId() ?: "",
            downloadUrl = download?.getDownloadUrl() ?: "",
            version = getCurrentVersion(),
            permissions = getPermissions(),
            translatableName = getSafeTranslations("name", safeLanguage),
            translatableDescription = getSafeTranslations("description", safeLanguage),
            translatableSummary = summary,
            iconUrl = getSafeString("icon_url"),
            siteUrl = getSafeString("url"),
            rating = getRating(),
            defaultLocale = (
                if (!safeLanguage.isNullOrEmpty() && isLanguageInTranslations) {
                    safeLanguage
                } else {
                    getSafeString("default_locale").ifEmpty { Addon.DEFAULT_LOCALE }
                }
                ).lowercase(Locale.ROOT),
        )
    }
}

internal fun JSONObject.getRating(): Addon.Rating? {
    val jsonRating = optJSONObject("ratings")
    return if (jsonRating != null) {
        Addon.Rating(
            reviews = jsonRating.optInt("count"),
            average = jsonRating.optDouble("average").toFloat(),
        )
    } else {
        null
    }
}

internal fun JSONObject.getCategories(): List<String> {
    val jsonCategories = optJSONObject("categories")
    return if (jsonCategories == null) {
        emptyList()
    } else {
        val jsonAndroidCategories = jsonCategories.getSafeJSONArray("android")
        (0 until jsonAndroidCategories.length()).map { index ->
            jsonAndroidCategories.getString(index)
        }
    }
}

internal fun JSONObject.getPermissions(): List<String> {
    val fileJson = getJSONObject("current_version")
        .getSafeJSONArray("files")
        .getJSONObject(0)

    val permissionsJson = fileJson.getSafeJSONArray("permissions")
    return (0 until permissionsJson.length()).map { index ->
        permissionsJson.getString(index)
    }
}

internal fun JSONObject.getCurrentVersion(): String {
    return optJSONObject("current_version")?.getSafeString("version") ?: ""
}

internal fun JSONObject.getDownload(): JSONObject? {
    return (
        getJSONObject("current_version")
            .optJSONArray("files")
            ?.getJSONObject(0)
        )
}

internal fun JSONObject.getDownloadId(): String {
    return getSafeString("id")
}

internal fun JSONObject.getDownloadUrl(): String {
    return getSafeString("url")
}

internal fun JSONObject.getAuthors(): List<Addon.Author> {
    val authorsJson = getSafeJSONArray("authors")
    return (0 until authorsJson.length()).map { index ->
        val authorJson = authorsJson.getJSONObject(index)

        Addon.Author(
            id = authorJson.getSafeString("id"),
            name = authorJson.getSafeString("name"),
            username = authorJson.getSafeString("username"),
            url = authorJson.getSafeString("url"),
        )
    }
}

internal fun JSONObject.getSafeString(key: String): String {
    return if (isNull(key)) {
        ""
    } else {
        getString(key)
    }
}

internal fun JSONObject.getSafeJSONArray(key: String): JSONArray {
    return if (isNull(key)) {
        JSONArray("[]")
    } else {
        getJSONArray(key)
    }
}

internal fun JSONObject.getSafeTranslations(valueKey: String, language: String?): Map<String, String> {
    // We can have two different versions of the JSON structure for translatable fields:
    // 1) A string with only one language, when we provide a language parameter.
    // 2) An object containing all the languages available when a language parameter is NOT present.
    // For this reason, we have to be specific about how we parse the JSON.
    return if (get(valueKey) is String) {
        val safeLanguage = (language ?: Addon.DEFAULT_LOCALE).lowercase(Locale.ROOT)
        mapOf(safeLanguage to getSafeString(valueKey))
    } else {
        getSafeMap(valueKey)
    }
}

internal fun JSONObject.getSafeMap(valueKey: String): Map<String, String> {
    return if (isNull(valueKey)) {
        emptyMap()
    } else {
        val map = mutableMapOf<String, String>()
        val jsonObject = getJSONObject(valueKey)

        jsonObject.keys()
            .forEach { key ->
                map[key.lowercase(Locale.ROOT)] = jsonObject.getSafeString(key)
            }
        map
    }
}

/**
 * Concatenates the given JSONArray onto this one.
 */
internal fun JSONArray.concat(other: JSONArray) {
    (0 until other.length()).map { index ->
        put(length(), other.getJSONObject(index))
    }
}
