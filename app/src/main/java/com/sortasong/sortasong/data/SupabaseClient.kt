package com.sortasong.sortasong.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    // TODO: Replace with your actual Supabase credentials
    private const val SUPABASE_URL = "https://hjzhojjnjnawwnwhzgwq.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_LATVvS_FmS-sLW_i1T7u1A_Bi9bWah7"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
    }

    fun isConfigured(): Boolean {
        // Returns true since credentials are now configured
        return SUPABASE_URL.isNotEmpty() && SUPABASE_ANON_KEY.isNotEmpty()
    }
}
