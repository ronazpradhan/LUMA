package com.stash.core.data.mix

import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.entity.StashMixRecipeEntity

/**
 * Ships Stash's built-in mix recipes. As of v0.9.40 there is a single
 * built-in flagship — **Daily Discover** (ARTIST_SIMILAR, 85% discovery /
 * 15% library) — and everything else is user-built via the Mix Builder.
 * The earlier "Deep Cuts" and "First Listen" builtins were retired:
 * `StashApplication.maybeRemoveRetiredBuiltinMixes` deletes them (and their
 * playlists) from existing installs; fresh installs never seed them.
 *
 * Only seeds when [StashMixRecipeDao.countBuiltins] is zero, so users
 * don't get defaults re-inserted every launch. Upgrades go through
 * `StashApplication.maybeRetuneStashMixes` which non-destructively updates
 * surviving built-in rows in place via [StashMixRecipeDao.retuneBuiltin].
 */
object StashMixDefaults {

    suspend fun seedIfNeeded(dao: StashMixRecipeDao) {
        if (dao.countBuiltins() > 0) return
        ALL.forEach { dao.insert(it) }
    }

    /**
     * LUMA: empty. Daily Discover, the last built-in, was retired — Home's
     * "Suggested for you" (ListeningSuggestions) replaces it, and
     * StashApplication.maybeRemoveRetiredBuiltinMixes deletes it from existing
     * installs. Every mix is now one the user builds in the Mix Builder.
     */
    val ALL: List<StashMixRecipeEntity> = emptyList()
}
