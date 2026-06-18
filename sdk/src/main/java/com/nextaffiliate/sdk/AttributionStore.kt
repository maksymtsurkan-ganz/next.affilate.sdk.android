package com.nextaffiliate.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence seam for the attribution state. Implemented over [SharedPreferences] in
 * production; an in-memory implementation is used in unit tests.
 */
internal interface AttributionStore {
    fun save(nxPb: String?, clickId: String?, source: AttributionSource)
    fun read(): Attribution?
    fun clearAttribution()
    fun isDeferredChecked(): Boolean
    fun markDeferredChecked()
}

/** Persistence keys shared by the contract across all platform SDKs. */
internal object StoreKeys {
    const val NX_PB = "next_affiliate_nx_pb"
    const val CLICK_ID = "next_affiliate_click_id"
    const val SOURCE = "next_affiliate_source"
    const val DEFERRED_CHECKED = "next_affiliate_deferred_checked"
    const val PREFS_NAME = "next_affiliate_sdk"
}

internal class SharedPrefsAttributionStore(
    private val prefs: SharedPreferences,
) : AttributionStore {

    constructor(context: Context) : this(
        context.applicationContext
            .getSharedPreferences(StoreKeys.PREFS_NAME, Context.MODE_PRIVATE),
    )

    override fun save(nxPb: String?, clickId: String?, source: AttributionSource) {
        prefs.edit()
            .putString(StoreKeys.NX_PB, nxPb)
            .putString(StoreKeys.CLICK_ID, clickId)
            .putString(StoreKeys.SOURCE, source.wireValue)
            .apply()
    }

    override fun read(): Attribution? {
        val nxPb = prefs.getString(StoreKeys.NX_PB, null)
        val clickId = prefs.getString(StoreKeys.CLICK_ID, null)
        val source = AttributionSource.fromWire(prefs.getString(StoreKeys.SOURCE, null))
        if (nxPb == null && clickId == null) return null
        if (source == null) return null
        return Attribution(nxPb = nxPb, clickId = clickId, source = source)
    }

    override fun clearAttribution() {
        prefs.edit()
            .remove(StoreKeys.NX_PB)
            .remove(StoreKeys.CLICK_ID)
            .remove(StoreKeys.SOURCE)
            .apply()
    }

    override fun isDeferredChecked(): Boolean =
        prefs.getBoolean(StoreKeys.DEFERRED_CHECKED, false)

    override fun markDeferredChecked() {
        prefs.edit().putBoolean(StoreKeys.DEFERRED_CHECKED, true).apply()
    }
}
