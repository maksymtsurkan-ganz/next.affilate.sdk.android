package com.nextaffiliate.sdk

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public entry point for the Next Affiliate attribution SDK.
 *
 * Recovers the signed attribution token `nx_pb` (and, for the deferred case, a `clickId`) and
 * exposes it so the host app can forward it into its S2S conversion / postback.
 *
 * All operations are best-effort and run on a background dispatcher: they never throw to the
 * host app and return `null` on any failure.
 *
 * ```
 * NextAffiliate.configure(context, NextAffiliateConfig(
 *     baseDomain = "next-ads-server-dev.com",
 *     scheme = "myapp",
 * ))
 * val attribution = NextAffiliate.handleLink(intent.dataString.orEmpty())
 * ```
 */
object NextAffiliate {

    @Volatile
    private var engine: AttributionEngine? = null

    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Configures the SDK. Must be called once (typically from `Application.onCreate`). */
    @JvmStatic
    fun configure(context: Context, config: NextAffiliateConfig) {
        configureInternal(
            config = config,
            http = UrlConnectionHttpClient(),
            store = SharedPrefsAttributionStore(context),
            dispatcher = Dispatchers.IO,
        )
    }

    /** Alias for [configure]. */
    @JvmStatic
    fun init(context: Context, config: NextAffiliateConfig) = configure(context, config)

    /**
     * Processes an incoming launch / deep-link URL:
     * - custom-scheme URL → reads `nx_pb` / `nx_click_id` query params (`source = scheme`);
     * - `https://.../trk/<shortCode>` → resolves the redirect (`source = universalLink`);
     * - anything else → ignored (returns `null`).
     */
    @JvmStatic
    suspend fun handleLink(url: String?): Attribution? {
        val e = engine ?: return null
        if (url.isNullOrEmpty()) return null
        return withContext(ioDispatcher) { e.handleLink(url) }
    }

    /** Performs the deferred match, guarded to run at most once per install. */
    @JvmStatic
    suspend fun checkDeferredOnFirstLaunch(): Attribution? {
        val e = engine ?: return null
        return withContext(ioDispatcher) { e.checkDeferredOnFirstLaunch() }
    }

    /** Returns the currently stored attribution, or `null` if none / not configured. */
    @JvmStatic
    fun getAttribution(): Attribution? = engine?.getAttribution()

    /** Wipes the stored attribution. Does not reset the deferred-checked guard. */
    @JvmStatic
    fun clearAttribution() {
        engine?.clearAttribution()
    }

    /** Test seam: inject mock collaborators. */
    internal fun configureInternal(
        config: NextAffiliateConfig,
        http: HttpClient,
        store: AttributionStore,
        dispatcher: CoroutineDispatcher,
    ) {
        engine = AttributionEngine(config, http, store)
        ioDispatcher = dispatcher
    }
}
