package com.nextaffiliate.sample

import android.app.Application
import com.nextaffiliate.sdk.NextAffiliate
import com.nextaffiliate.sdk.NextAffiliateConfig

/** Dev base domain used throughout the sample. */
const val DEV_BASE_DOMAIN = "next-ads-server-dev.com"

/** App custom URL scheme; must match the manifest intent-filter. */
const val APP_SCHEME = "myapp"

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NextAffiliate.configure(
            this,
            NextAffiliateConfig(
                baseDomain = DEV_BASE_DOMAIN,
                scheme = APP_SCHEME,
            ),
        )
    }
}
