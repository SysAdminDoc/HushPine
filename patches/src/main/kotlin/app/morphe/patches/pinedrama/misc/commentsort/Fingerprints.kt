/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e1fb74c7
 */
package app.morphe.patches.pinedrama.misc.commentsort

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.pinedrama.shared.readsLazyAb
import app.morphe.patches.pinedrama.shared.resolveLazyAbGate

/** The setting the comment sort menu is shaped by, which both anchors below rest on. */
internal const val COMMENT_SORT_STYLE_KEY = "comment_sort_opt_style"

/**
 * The rollout gate. Anchored on the string rather than on the obfuscated lambda class upstream
 * names, because that name changes every build; the string does not. Parameter and return types
 * match by prefix, so "L" is any object.
 *
 * <p>This is the looser of the two anchors and so the one that could bind quietly: the patcher
 * takes the first match and does not complain about a second. In 46.2.3 exactly two methods carry
 * the string and only one has this shape, so there is nothing to pick between; on a build with
 * more, this would bind to whichever came first rather than fail. The eligibility anchor below
 * refuses a second candidate by name instead.
 */
/**
 * Every read of the style, which the patch hooks one by one.
 *
 * <p>The lazy read takes at most one object parameter and answers an object. It was an
 * R8-outlined static body taking its captured receiver up to 46.8.3, a zero-parameter instance
 * `invoke()` on 46.9.3, and a body in a merged lambda group on 48.0.3; the patch follows the key's
 * register and reads no parameter, so any of those shapes will do. 48.0.3 adds an eager read
 * beside it, a no-argument `()I` getter that `kt_lazy_load_exp` sends the lookup to instead of the
 * lazy one, so a hook on the lazy read alone would miss half the installs. The other carrier of
 * the key on every build is a `()V` settings registrar, which neither shape lets in.
 */
internal object CommentSortOptionStyleFingerprint : Fingerprint(
    strings = listOf(COMMENT_SORT_STYLE_KEY),
    custom = { method, _ ->
        val lazyRead = method.returnType.startsWith("L") &&
            method.parameterTypes.size <= 1 && method.parameterTypes.all { it.startsWith("L") }
        val eagerRead = method.returnType == "I" && method.parameterTypes.isEmpty()
        lazyRead || eagerRead
    },
)

/**
 * The per-post eligibility check.
 *
 * <p>Its class was written here as `LX/0nmj;` and it carries no string of its own, so there was
 * nothing to hold on to but that name. What it does have is a lazily read AB value, and the key
 * that value reads is `comment_sort_opt_style`, the same one the style fingerprint above is
 * anchored on. That key is PineDrama's own name and is on all three builds.
 */
internal fun BytecodePatchContext.resolveCommentSortEligibility(): MutableMethod =
    resolveLazyAbGate("Comment sort controls", COMMENT_SORT_STYLE_KEY) { method, owner ->
        method.returnType == "Z" &&
            method.parameterTypes.map(CharSequence::toString) ==
            listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;") &&
            method.readsLazyAb(owner)
    }
