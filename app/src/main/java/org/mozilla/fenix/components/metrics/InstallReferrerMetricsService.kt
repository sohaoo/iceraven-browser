/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.content.Context
import android.net.UrlQuerySanitizer
import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import org.mozilla.fenix.GleanMetrics.PlayStoreAttribution
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.utils.Settings
import java.net.URLDecoder

/**
 * A metrics service used to derive the UTM parameters with the Google Play Install Referrer library.
 *
 * At first startup, the [UTMParams] are derived from the install referrer URL and stored in settings.
 */
class InstallReferrerMetricsService(private val context: Context) : MetricsService {
    override val type = MetricServiceType.Marketing

    override fun start() {
        if (context.settings().utmParamsKnown) {
            return
        }
    }

    override fun stop() {
    }

    override fun track(event: Event) = Unit

    override fun shouldTrack(event: Event): Boolean = false

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun recordInstallReferrer(settings: Settings, url: String?) {
        if (url.isNullOrBlank()) {
            return
        }
        val params = UTMParams.fromURLString(url)
        if (params == null || params.isEmpty()) {
            return
        }
        params.intoSettings(settings)

        params.apply {
            source?.let {
                PlayStoreAttribution.source.set(it)
            }
            medium?.let {
                PlayStoreAttribution.medium.set(it)
            }
            campaign?.let {
                PlayStoreAttribution.campaign.set(it)
            }
            content?.let {
                PlayStoreAttribution.content.set(it)
            }
            term?.let {
                PlayStoreAttribution.term.set(it)
            }
        }
    }
}

/**
 * Descriptions of utm parameters comes from
 * https://support.google.com/analytics/answer/1033863
 * - utm_source
 *  Identify the advertiser, site, publication, etc.
 *  that is sending traffic to your property, for example: google, newsletter4, billboard.
 * - utm_medium
 *  The advertising or marketing medium, for example: cpc, banner, email newsletter.
 * utm_campaign
 *  The individual campaign name, slogan, promo code, etc. for a product.
 * - utm_term
 *  Identify paid search keywords.
 *  If you're manually tagging paid keyword campaigns, you should also use
 *  utm_term to specify the keyword.
 * - utm_content
 *  Used to differentiate similar content, or links within the same ad.
 *  For example, if you have two call-to-action links within the same email message,
 *  you can use utm_content and set different values for each so you can tell
 *  which version is more effective.
 */
data class UTMParams(
    val source: String?,
    val medium: String?,
    val campaign: String?,
    val term: String?,
    val content: String?,
) {
    companion object {
        const val UTM_SOURCE = "utm_source"
        const val UTM_MEDIUM = "utm_medium"
        const val UTM_CAMPAIGN = "utm_campaign"
        const val UTM_TERM = "utm_term"
        const val UTM_CONTENT = "utm_content"

        /**
         * Try and unpack the referrer URL by successively URLDecoding the URL.
         *
         * Once the url ceases to decode anymore, it gives up.
         */
        fun fromURLString(urlString: String): UTMParams? {
            // Look for the first time 'utm_' is detected, after the first '?'.
            val utmIndex = urlString.indexOf("utm_", urlString.indexOf('?'))
            if (utmIndex < 0) {
                return null
            }
            var url = urlString.substring(utmIndex)
            while (true) {
                val params = fromQueryString(url)
                if (!params.isEmpty()) {
                    return params
                }
                val newValue = URLDecoder.decode(url, "UTF-8")
                if (newValue == url) {
                    break
                }
                url = newValue
            }
            return null
        }

        /**
         * Derive a set of UTM parameters from a string URL.
         */
        fun fromQueryString(queryString: String): UTMParams =
            with(UrlQuerySanitizer()) {
                allowUnregisteredParamaters = true
                unregisteredParameterValueSanitizer = UrlQuerySanitizer.getUrlAndSpaceLegal()
                parseQuery(queryString)
                UTMParams(
                    source = getValue(UTM_SOURCE),
                    medium = getValue(UTM_MEDIUM),
                    campaign = getValue(UTM_CAMPAIGN),
                    term = getValue(UTM_TERM),
                    content = getValue(UTM_CONTENT),
                )
            }

        /**
         * Derive the set of UTM parameters stored in Settings.
         */
        fun fromSettings(settings: Settings): UTMParams =
            with(settings) {
                UTMParams(
                    source = utmSource,
                    medium = utmMedium,
                    campaign = utmCampaign,
                    term = utmTerm,
                    content = utmContent,
                )
            }
    }

    /**
     * Persist the UTM params into Settings.
     */
    fun intoSettings(settings: Settings) {
        with(settings) {
            utmSource = source ?: ""
            utmMedium = medium ?: ""
            utmCampaign = campaign ?: ""
            utmTerm = term ?: ""
            utmContent = content ?: ""
        }
    }

    /**
     * Return [true] if none of the utm params are set.
     */
    fun isEmpty(): Boolean {
        return source.isNullOrBlank() &&
            medium.isNullOrBlank() &&
            campaign.isNullOrBlank() &&
            term.isNullOrBlank() &&
            content.isNullOrBlank()
    }
}
