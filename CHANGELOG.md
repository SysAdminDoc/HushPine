# Changelog

## Unreleased

### Features

* `Keep list position` is new. History and Favorites on your profile hold your place when you switch tabs or come back from a video, and a list you haven't scrolled still shows what you just watched. It's on by default.

### Changes

* Retargeted to PineDrama 47.6.3 (version code 2024706030). All the patches apply to it, and the patches that hook the app's own code were re-read against the new build's code instead of carried over by name.
* Auto-advance no longer depends on a panel check 47.6.3 dropped, and the streak button patch lets go of a getter the new build removed.
* The Feature Gate Lab's Manage topics and More hashtags per post presets are gone, and AI Self sets one rule fewer. PineDrama 47.6.3 took those gates out of the app.

### Bug Fixes

* What's new shows up again on a PineDrama you first patched with Hushfeed's bundle. The version you'd dismissed there was higher than HushPine's, so the row stayed hidden.
* Picking another language on PineDrama's App language page and tapping Done switches the app's language again. PineDrama waited on a Play Store language download that a patched copy never gets, so nothing changed.

## 0.0.1 (2026-11-01)

### Features

* HushPine starts here. The PineDrama patches used to ship inside Hushfeed's bundle, and they now live in their own repo with their own bundle and signing key. Your settings and saved data carry over when you move from Hushfeed's patches.
* Press and hold any tab at the top of the feed to open HushPine's settings. It used to work on one tab only, and PineDrama's server decides which middle tab you get, so the shortcut could disappear.
* Latest first on Following now leaves alone what you've already scrolled past. It used to reorder the whole list each time PineDrama read it.
* New saves go to `DCIM/PineDrama` by default instead of `DCIM/TikTok`. A folder you picked yourself stays as it is, and files you've already saved don't move.
* Settings no longer describe buttons and rows PineDrama doesn't have. The story save rows are gone, and so are the Balance and TikTok Minis rows for the profile menu, since PineDrama's gear goes straight to Settings and privacy.

